/*
 * <copyright>
 *  Copyright 2000-2003 BBNT Solutions, LLC
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

import javax.servlet.Servlet;

import org.cougaar.core.component.*;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ServletService;
import org.cougaar.core.service.wp.*;
import org.cougaar.core.node.NodeControlService;

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
 * @property org.cougaar.lib.web.http.factory
 *   Optional classname for factory used to create HTTP server
 *   sockets.  Defaults to a standard socket factory.
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
 *
 * @property org.cougaar.lib.web.https.factory
 *   Optional classname for factory used to create HTTPS (SSL) 
 *   server sockets.  Defaults to a standard SSL socket factory.
 * </pre>
 *
 * @see ServletService
 */
public class RootServletServiceComponent 
extends org.cougaar.util.GenericStateModelAdapter
implements Component 
{
  private LoggingService log;

  // used to create the "rootReg"
  private WhitePagesService wp;

  private ServiceBroker sb;

  // from initialize
  private String serverClassname;
  private Object serverArg;
  private int scanRange;

  private Map serverOptions;
  private int initHttpPort;
  private int initHttpsPort;
  private Map httpOptions;
  private Map httpsOptions;

  private GlobalRegistry globReg;
  private ServiceProvider rootSP;
  private ServletEngine servEng;
  private ServletRegistry rootReg;

  // actual HTTP/HTTPS ports that were used, in case the 
  // initial ports (initHttp[s]Port) are already in use.
  private int usedHttpPort;
  private int usedHttpsPort;

  public void setBindingSite(BindingSite bs) {
    // only care about the service broker
    //this.sb = bs.getServiceBroker();
  }

  public void setNodeControlService(NodeControlService ncs) {
    this.sb = ncs.getRootServiceBroker();
  }

  public void setParameter(Object o) {
    throw new UnsupportedOperationException(
        "Root servlet-service not expecting a parameter: "+
        ((o != null) ? o.getClass().getName() : "null"));
  }

  private void configureParameters() {
    // this occurs after "load()" to allow logging

    // all paths are relative to the "$org.cougaar.install.path"
    String cip =
      System.getProperty("org.cougaar.install.path");

    // set the server parameters
    this.scanRange = 
      Integer.getInteger(
          "org.cougaar.lib.web.scanRange",
          100).intValue();

    if (scanRange < 1) {
      throw new IllegalArgumentException(
          "Port scan range must be at least 1, not \""+
          scanRange+"\"");
    }

    // hard-code the servlet engine implementation
    this.serverClassname = 
      "org.cougaar.lib.web.tomcat.TomcatServletEngine";
    this.serverArg = 
      cip+"/webtomcat/data";

    // set the http and https ports
    this.initHttpPort = 
      Integer.getInteger(
          "org.cougaar.lib.web.http.port",
          8800).intValue();
    this.initHttpsPort = 
      Integer.getInteger(
          "org.cougaar.lib.web.https.port",
          (-1)).intValue();
    if ((initHttpPort  > 0) &&
        (initHttpsPort > 0) &&
        (initHttpPort == initHttpsPort)) {
      throw new IllegalArgumentException(
          "HTTP port ("+initHttpPort+
          ") and HTTPS port ("+initHttpsPort+
          ") must be different!");
    }

    // for now these protocol options are fixed.
    //
    // in the future this may be expanded to support
    // new options, such as socket timeouts, etc.

    // use our logging level to set the server's logging level
    serverOptions = 
      Collections.singletonMap(
          "debug", 
          Boolean.toString(log.isDebugEnabled()));

    httpOptions = Collections.EMPTY_MAP;
    if (initHttpPort > 0) {
      String httpFactory =
        System.getProperty(
            "org.cougaar.lib.web.http.factory");
      if (httpFactory != null) {
        httpOptions = 
          Collections.singletonMap("factory", httpFactory);
      }
    }

    if (initHttpsPort > 0) {
      this.httpsOptions = new HashMap(13);
      String httpsFactory =
        System.getProperty(
            "org.cougaar.lib.web.https.factory");
      if (httpsFactory != null) {
        httpsOptions.put("factory", httpsFactory);
      }

      String clientAuth = 
        System.getProperty(
            "org.cougaar.lib.web.https.clientAuth");
      if (clientAuth != null) {
        httpsOptions.put("clientAuth", clientAuth);
      }

      // config data from the "cougaar.rc", for security reasons
      String serverKeystore = 
        Parameters.findParameter("org.cougaar.web.keystore");
      if (serverKeystore != null) {
        // keystore is relative to "$org.cougaar.install.path"
        serverKeystore = cip+"/"+serverKeystore;
        httpsOptions.put("keystore", serverKeystore);
      }

      String serverKeypass  = 
        Parameters.findParameter("org.cougaar.web.keypass");
      if (serverKeypass != null) {
        httpsOptions.put("keypass", serverKeypass);
      }

      // these are hard-coded
      httpsOptions.put("keyname", "tomcat");
      if (serverKeystore != null) {
        httpsOptions.put("trustKeystore", serverKeystore);
      }
    } else {
      httpsOptions = Collections.EMPTY_MAP;
    }

  }

  public void load() {
    super.load();

    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);
    if (log == null) {
      log = LoggingService.NULL;
    }

    configureParameters();

    if (log.isDebugEnabled()) {
      log.debug(
          "Server options: "+serverOptions);
      log.debug(
          "HTTP port: "+initHttpPort+
          " Options: "+httpOptions);
      log.debug(
          "HTTPS port: "+initHttpsPort+
          " Options: "+httpsOptions);
    }

    // create the global registry
    try {
      // get the white pages service
      wp = (WhitePagesService)
        sb.getService(this, WhitePagesService.class, null);
      if (wp == null) {
        throw new RuntimeException(
            "Root servlet-service unable to"+
            " obtain WhitePagesService");
      }

      // create a server registry
      this.globReg = new NamingServerRegistry(wp);
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

    if (log.isDebugEnabled()) {
      log.debug(
          "Root server ("+servEng+") is running");
    }
  }

  public void start() {
    super.start();

    try {
      // start the server
      startServer();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }

    try {
      // configure the reg, which alters the rootReg
      globReg.configure(usedHttpPort, usedHttpsPort);
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

    // release the white pages service
    if (wp != null) {
      sb.releaseService(this, WhitePagesService.class, wp);
      wp = null;
    }
    if ((log != null) && (log != LoggingService.NULL)) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
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

    // set the scan range
    int maxI = scanRange;

    // FIXME notice |httpsPort-httpPort|, adjust maxI
    //
    // would make scanning more efficient...

    int httpPort  = initHttpPort;
    int httpsPort = initHttpsPort;

    // scan the ports, try to launch the server
    for (int i = 0; ; i++) {

      if (i >= maxI) {
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
        if (log.isErrorEnabled()) {
          log.error(msg);
        }
        throw new java.net.BindException(msg);
      }

      if (log.isDebugEnabled()) {
        log.debug(
            "Server launch attempt["+i+" / "+maxI+"]:"+
            ((httpPort > 0) ? 
             (" HTTP  port ("+httpPort+")") :
             (""))+
            ((httpPort > 0) ? 
             (" HTTPS port ("+httpsPort+")") :
             ("")));
      }

      try {
        startServer(httpPort, httpsPort);
        break;
      } catch (java.net.BindException be) {
        // port(s) in use, try again
      }

      if (httpPort > 0) {
        ++httpPort;
      }
      if (httpsPort > 0) {
        ++httpsPort;
      }
    }

    // success; save the config
    this.usedHttpPort  = httpPort;
    this.usedHttpsPort = httpsPort;

    if (log.isInfoEnabled()) {
      log.info(
          "\nServer launched with: "+
          ((httpPort > 0) ? 
           ("\nHTTP : "+usedHttpPort) :
           (""))+
          ((httpPort > 0) ? 
           ("\nHTTPS: "+usedHttpsPort) :
           ("")));
    }
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

    // ports seem free -- try to launch the full server
    //
    // note that another process might grab the ports,
    // in which case a "javax.net.BindingException" will
    // be thrown.  This is okay so long as the caller can
    // attempt "servEng.start()" again.
    servEng.configure(
        serverOptions,
        httpPort, httpOptions, 
        httpsPort, httpsOptions);
    servEng.start();

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
        return usedHttpPort;
      }

      public int getHttpsPort() {
        return usedHttpsPort;
      }
    }
  }

}
