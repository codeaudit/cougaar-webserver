/*
 * <copyright>
 *  Copyright 2000-2001 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */
package org.cougaar.lib.web.service;

import java.io.IOException;
import java.util.*;

import javax.naming.directory.DirContext;

import javax.servlet.Servlet;

import org.cougaar.core.component.*;
import org.cougaar.core.service.NamingService;
import org.cougaar.core.servlet.ServletService;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.root.*;
import org.cougaar.lib.web.arch.server.*;

import org.cougaar.util.Parameters;

/**
 * A root-level (Node) <code>Component</code> that adds a
 * <code>ServiceProvider</code> for <code>ServletService</code>, 
 * which is be used to register <code>Servlet</code>s.
 * <p>
 * For example, Node "N" can create a new root-level ServletService 
 * its children.  When a child, such as Agent "A", requests
 * ServletService it will be able to register itself as "/$A/".
 * <p>
 * For HTTPS the "cougaar.rc" must contain:<pre>
 *   org.cougaar.web.keystore=FILENAME
 *   org.cougaar.web.keypass=Password
 * </pre>
 * where the FILENAME is relative to the "$cougaar.install.path".
 * See <code>org.cougaar.util.Parameters</code> for "cougaar.rc" 
 * documentation.
 * <p>
 * 
 * <pre>
 * @property org.cougaar.lib.web.scanRange
 *   The scan range for ports beyond the configured base HTTP
 *   and HTTPS ports.  If either port is in use the ports will 
 *   be incremented by one until "scanRange" ports have been 
 *   tried, at which time a java.net.BindingException will be 
 *   thrown.  The default is 100, which (if no other ports are
 *   being used, and no change to the below "http[s].port" 
 *   parameters) would allow for 100 Nodes on a machine.
 *
 * @property org.cougaar.lib.web.http.port
 *   The base integer port for the HTTP server, which defaults to
 *   8800.  The most common value is 8800.  If a negative number 
 *   is passed then HTTP listening is disabled.  Also see the 
 *    "org.cougaar.lib.web.scanRange" property.
 *
 * @property org.cougaar.lib.web.https.port
 *   The base integer port for the HTTPS server, which defaults to
 *   -1.  The most common value is 8400.  If a negative number is 
 *   passed then HTTPS listening is disabled.  Also see the 
 *   "org.cougaar.lib.web.scanRange" property.
 *
 * @property org.cougaar.lib.web.https.clientAuth
 *   Used to enable HTTPS client-authentication.  Defaults to
 *   false.
 * </pre>
 *
 * @see ServletService
 */
public class RootServletServiceComponent 
extends org.cougaar.util.GenericStateModelAdapter
implements Component 
{
  // used to create the "rootReg"
  private NamingService ns;

  private ServiceBroker sb;

  // from initialize
  private String serverClassname;
  private Object serverArg;
  private HttpConfig initHttpConfig;
  private HttpsConfig initHttpsConfig;
  private int scanRange;
  private boolean debug;

  private GlobalRegistry globReg;
  private ServiceProvider rootSP;
  private ServletEngine servEng;
  private ServletRegistry rootReg;

  // actual HTTP/HTTPS configs that were used.
  //
  // only different than initHttp[s]Config if the suggested
  // port(s) was in use.
  private HttpConfig usedHttpConfig;
  private HttpsConfig usedHttpsConfig;

  public void setBindingSite(BindingSite bs) {
    // only care about the service broker
    this.sb = bs.getServiceBroker();
  }

  public void initialize() {
    super.initialize();

    // config data from system properties:
    this.scanRange = 
      Integer.getInteger(
          "org.cougaar.lib.web.scanRange",
          100).intValue();
    int httpPort = 
      Integer.getInteger(
          "org.cougaar.lib.web.http.port",
          8800).intValue();
    int httpsPort = 
      Integer.getInteger(
          "org.cougaar.lib.web.https.port",
          (-1)).intValue();
    boolean clientAuth =
      Boolean.getBoolean(
          "org.cougaar.lib.web.https.clientAuth");

    // paths are relative to the "$org.cougaar.install.path"
    String cip =
      System.getProperty("org.cougaar.install.path");

    // config data from the "cougaar.rc", for security reasons
    //
    // private keystore/keypass for HTTPS
    String serverKeystore = null;
    String serverKeypass = null;
    if (httpsPort > 0) {
      // look in the "cougaar.rc"
      serverKeystore = 
        Parameters.findParameter("org.cougaar.web.keystore");
      serverKeypass  = 
        Parameters.findParameter("org.cougaar.web.keypass");
      if ((serverKeystore == null) ||
          (serverKeypass == null)) {
        throw new RuntimeException(
            "HTTPS requires \"cougaar.rc\" to contain both"+
            " \"org.cougaar.web.keystore=..\" and"+
            " \"org.cougaar.web.keypass=..\" entries");
      }
      // keystore is relative to "$org.cougaar.install.path"
      serverKeystore = cip+"/"+serverKeystore;
    }

    // hard-code the servlet engine implementation
    this.serverClassname = 
      "org.cougaar.lib.web.tomcat.TomcatServletEngine";
    this.serverArg = 
      cip+"/webtomcat/data";
    String serverKeyname = "tomcat";
    String trustKeystore = serverKeystore;

    //
    // examine the parameters
    //

    if (scanRange < 1) {
      throw new IllegalArgumentException(
          "Port scan range must be at least 1, not \""+
          scanRange+"\"");
    }

    if ((httpPort  > 0) &&
        (httpsPort > 0) &&
        (httpPort == httpsPort)) {
      throw new IllegalArgumentException(
          "HTTP port ("+httpPort+
          ") and HTTPS port ("+httpsPort+
          ") must be different!");
    }

    // create the HTTP  config
    //
    // this is the *suggested* config.  If the "httpPort"
    // is in use then another port will be used.
    this.initHttpConfig = 
      ((httpPort > 0) ?
       (new HttpConfig(httpPort)) :
       (null));

    // create the HTTPS config
    this.initHttpsConfig =
      ((httpsPort > 0) ?
       (new HttpsConfig(
          new HttpConfig(httpsPort),
          clientAuth,
          serverKeystore,
          serverKeypass,
          serverKeyname,
          trustKeystore)) :
       (null));

    if (debug) {
      System.out.println(
          "Initialized server with "+
          ((initHttpConfig != null) ?
           ("\nHTTP : "+initHttpConfig) :
           (""))+
          ((initHttpsConfig != null) ?
           ("\nHTTPS: "+initHttpsConfig) :
           ("")));
    }
  }

  public void setParameter(Object o) {
    throw new UnsupportedOperationException(
        "Root servlet-service not expecting a parameter: "+
        ((o != null) ? o.getClass().getName() : "null"));
  }

  public void load() {
    super.load();

    // create the global registry
    try {
      // get the naming service
      ns = (NamingService)
        sb.getService(this, NamingService.class, null);
      if (ns == null) {
        throw new RuntimeException(
            "Root servlet-service unable to"+
            " obtain NamingService");
      }

      // get the root naming directory
      DirContext rootDir = ns.getRootContext();

      // create a server registry
      this.globReg = new NamingServerRegistry(rootDir);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }

    try {
      // create and configure the server
      this.servEng = createServer(serverClassname, serverArg);
      configureRootServlet();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }

    if (debug) {
      System.out.println(
          "Root server ("+servEng+") is running");
    }
  }

  public void start() {
    super.start();

    try {
      // start the server, set the "usedHttp[s]Config"s
      startServer();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }

    try {
      // configure the reg, which alters the rootReg
      globReg.configure(usedHttpConfig, usedHttpsConfig);
    } catch (RuntimeException re) {
      servEng.stop();
      throw re;
    } catch (Exception e) {
      servEng.stop();
      throw new RuntimeException(e.getMessage());
    }

    // create and advertise our service
    this.rootSP = new RootServletServiceProviderImpl();
    sb.addService(ServletService.class, rootSP);
  }

  public void stop() {
    try {
      // revoke our service
      if (rootSP != null) {
        sb.revokeService(ServletService.class, rootSP);
        rootSP = null;
      }

      servEng.stop();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
    super.stop();
  }

  public void unload() {

    // release the naming service
    if (ns != null) {
      sb.releaseService(this, NamingService.class, ns);
      ns = null;
    }

    super.unload();
  }

  //
  // private utility methods and classes:
  //

  private static ServletEngine createServer(
      String classname,
      Object arg) throws Exception {
    // basic reflection code:
    Class servClass = Class.forName(classname);
    if (!(ServletEngine.class.isAssignableFrom(servClass))) {
      throw new IllegalArgumentException(
          "Class \""+classname+"\" does not implement \""+
          ServletEngine.class.getName()+"\"");
    }
    java.lang.reflect.Constructor servCons = 
      servClass.getConstructor(new Class[]{Object.class});
    Object ret;
    try {
      ret = servCons.newInstance(new Object[]{arg});
    } catch (java.lang.reflect.InvocationTargetException ite) {
      // extract wrapped Exception
      Throwable t = ite.getTargetException();
      if (t instanceof Exception) {
        throw (Exception) t;
      } else {
        throw ite;
      }
    }
    return (ServletEngine) ret;
  }

  private void configureRootServlet() throws Exception {

    this.rootReg = 
      new RootServletRegistry(globReg);
    Servlet noNameServlet = 
      new RootNonNameServlet(rootReg, globReg);
    Servlet unknownNameServlet = 
      new UnknownRootNameServlet(rootReg);
    Servlet remoteNameServlet = 
      new RootRedirectServlet(
          globReg,
          unknownNameServlet);
    RootServlet rootServlet = 
      new RootServlet(
          rootReg, 
          noNameServlet, 
          noNameServlet, 
          remoteNameServlet);

    servEng.setGateway(rootServlet);
  }

  /**
   * Start the server, scan the port range based upon the
   * "org.cougaar.lib.web.scanRange" property.
   * <p>
   * See the "@property" javadocs at the top of this file.
   */
  private void startServer() throws Exception {

    int httpPort  = 
      ((initHttpConfig  != null) ? 
       initHttpConfig.getPort() : 
       -1);
    int httpsPort = 
      ((initHttpsConfig != null) ? 
       initHttpsConfig.getHttpConfig().getPort() : 
       -1);

    // set the scan range
    int maxI = scanRange;

    // FIXME notice |httpsPort-httpPort|, adjust maxI
    //
    // would make scanning more efficient...

    // try to start the server.
    for (int i = 0; i < maxI; i++) {

      if (debug) {
        System.out.println(
            "Server launch attempt["+i+" / "+maxI+"]:"+
            ((httpPort > 0) ? 
             (" HTTP  port ("+(httpPort+i)+")") :
             (""))+
            ((httpPort > 0) ? 
             (" HTTPS port ("+(httpsPort+i)+")") :
             ("")));
      }

      try {
        startServer(
            ((httpPort  > 0) ? (httpPort  + i) : (-1)),
            ((httpsPort > 0) ? (httpsPort + i) : (-1)));
      } catch (java.net.BindException be) {
        // port(s) in use, try again
        continue;
      }

      // success
      if (debug) {
        System.out.println(
            "\nServer launched with: "+
            ((httpPort > 0) ? 
             ("\nHTTP : "+usedHttpConfig) :
             (""))+
            ((httpPort > 0) ? 
             ("\nHTTPS: "+usedHttpsConfig) :
             ("")));
      }

      return;
    }

    // failure; tried too many ports
    String msg = 
      "Unable to launch server"+
      ((httpPort > 0) ? 
       (", attempted "+maxI+" HTTP  ports ("+
        httpPort+"-"+(httpPort +(maxI-1))+")") :
       (""))+
      ((httpPort > 0) ? 
       (", attempted "+maxI+" HTTPS ports ("+
        httpsPort+"-"+(httpsPort+(maxI-1))+")") :
       (""));
    if (debug) {
      System.out.println(msg);
    }
    throw new java.net.BindException(msg);
  }

  /**
   * Start a server at the given HTTP/HTTPS ports.
   *
   * @throws java.net.BindException if a port is in use
   * @throws Exception some other problem
   */
  private void startServer(
      int  httpPort,
      int httpsPort) throws Exception {

    // quick-check to see if the ports are free
    if (httpPort  > 0) {
      (new java.net.ServerSocket(httpPort )).close();
    }
    if (httpsPort > 0) {
      (new java.net.ServerSocket(httpsPort)).close();
    }

    // create new HTTP[s] configs with the new ports
    HttpConfig httpConfig = 
      ((httpPort > 0) ?
       ((httpPort != initHttpConfig.getPort()) ? 
        (new HttpConfig(initHttpConfig, httpPort)) :
        (initHttpConfig)) :
       (null));
    HttpsConfig httpsConfig =
      ((httpsPort > 0) ?
       ((httpsPort != initHttpsConfig.getHttpConfig().getPort()) ? 
        (new HttpsConfig(initHttpsConfig, httpsPort)) :
        (initHttpsConfig)) :
       (null));

    // ports seem free -- try to launch the full server
    //
    // note that another process might grab the ports,
    // in which case a "javax.net.BindingException" will
    // be thrown.  This is okay so long as the caller can
    // attempt "servEng.start()" again.
    servEng.configure(httpConfig, httpsConfig, debug);
    servEng.start();

    // success; save the config
    this.usedHttpConfig  = httpConfig;
    this.usedHttpsConfig = httpsConfig;
  }

  /**
   * Service provider for our <code>ServletService</code>.
   */
  private class RootServletServiceProviderImpl
  implements ServiceProvider {

    public Object getService(
        ServiceBroker sb, 
        Object requestor, 
        Class serviceClass) {
      // create a new service instance
      if (serviceClass == ServletService.class) {
        return new RootServletServiceImpl();
      } else {
        throw new IllegalArgumentException(
            "ServletService does not provide a service for: "+
            serviceClass);
      }
    }

    public void releaseService(
        ServiceBroker sb, 
        Object requestor, 
        Class serviceClass, 
        Object service)  {
      // unregister all servlets of this service instance
      if (!(service instanceof RootServletServiceImpl)) {
        throw new IllegalArgumentException(
            "ServletService unable to release service: "+
            ((service != null) ? service.getClass().toString() : "null"));
      }
      RootServletServiceImpl rssi =
        (RootServletServiceImpl)service;
      rssi.unregisterAll();
    }

    private class RootServletServiceImpl
    implements ServletService {

      // List of paths registered by this requestor; typically a 
      // tiny list.
      //
      // All the methods of this impl are synchronized on this 
      // list.  Partially this is for List safety, but also
      // it makes sense that one shouldn't be able to 
      // simultaneously register and unregister a Servlet...
      private final List l = new ArrayList(3);

      public void register(
          String path,
          Servlet servlet) throws Exception {
        synchronized (l) {
          rootReg.register(path, servlet);
          l.add(path);
        }
      }

      public void unregister(
          String path) {
        synchronized (l) {
          rootReg.unregister(path);
          l.remove(path);
        }
      }

      public void unregisterAll() {
        // unregister all servlets
        synchronized (l) {
          for (int n = l.size() - 1; n >= 0; n--) {
            String path = (String)l.get(n);
            rootReg.unregister(path);
          }
          l.clear();
        }
      }

      public int getHttpPort() {
        return 
          ((usedHttpConfig != null) ?
           (usedHttpConfig.getPort()) : 
           (-1));
      }

      public int getHttpsPort() {
        return 
          ((usedHttpsConfig != null) ?
           (usedHttpsConfig.getHttpConfig().getPort()) : 
           (-1));
      }
    }
  }

}
