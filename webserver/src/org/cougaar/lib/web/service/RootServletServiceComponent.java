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
import java.net.InetAddress;
import java.util.*;

import javax.naming.directory.DirContext;

import javax.servlet.Servlet;

import org.cougaar.core.component.*;
import org.cougaar.core.naming.NamingService;
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

  // from parameters:
  private String serverClassname;
  private Object serverArg;
  private HttpConfig httpConfig;
  private HttpsConfig httpsConfig;
  private boolean debug;

  private ServiceProvider rootSP;
  private ServletEngine servEng;
  private ServletRegistry rootReg;

  public void setBindingSite(BindingSite bs) {
    // only care about the service broker
    this.sb = bs.getServiceBroker();
  }

  public void initialize() {
    super.initialize();

    // hard-code the servlet engine implementation
    this.serverClassname = 
      "org.cougaar.lib.web.tomcat.TomcatServletEngine";
    this.serverArg = 
      "webtomcat/data";

    // config data, maybe from system properties:
    int httpPort = 8800;  // FIXME make this (nsPort + 100)?
    int httpsPort = 8400; // FIXME make this (httpPort + 100)?
    boolean clientAuth = false; // FIXME make a parameter?

    // private keystore/keypass for HTTPS
    String serverKeystore = null;
    String serverKeypass = null;
    if (httpsPort > 0) {
      // look in the "cougaar.rc"
      serverKeystore = Parameters.findParameter("org.cougaar.web.keystore");
      serverKeypass  = Parameters.findParameter("org.cougaar.web.keypass");
      if ((serverKeystore == null) ||
          (serverKeypass == null)) {
        throw new RuntimeException(
            "HTTPS requires \"cougaar.rc\" to contain both"+
            " \"org.cougaar.web.keystore=..\" and"+
            " \"org.cougaar.web.keypass=..\" entries");
      }
    }

    // use of tomcat forces these two parameters:
    String serverKeyname = "tomcat";
    String trustKeystore = serverKeystore;

    // prepend keystore paths with install-path
    String cip = System.getProperty("org.cougaar.install.path");
    if ((serverKeystore != null) &&
        (!(serverKeystore.startsWith("/")))) {
      serverKeystore = cip+"/"+serverKeystore;
    }
    if ((trustKeystore != null) &&
        (!(trustKeystore.startsWith("/")))) {
      trustKeystore = cip+"/"+trustKeystore;
    }

    // server always runs on localhost
    InetAddress localAddr;
    try {
      localAddr = InetAddress.getLocalHost();
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to get localhost address: "+e.getMessage());
    }

    // create the HTTP  config
    this.httpConfig = 
      ((httpPort > 0) ?
       (new HttpConfig(localAddr, httpPort)) :
       (null));

    // create the HTTPS config
    this.httpsConfig =
      ((httpsPort > 0) ?
       (new HttpsConfig(
          new HttpConfig(localAddr, httpsPort),
          clientAuth,
          serverKeystore,
          serverKeypass,
          serverKeyname,
          trustKeystore)) :
       (null));
  }

  public void setParameter(Object o) {
    throw new UnsupportedOperationException(
        "Root servlet-service not expecting a parameter: "+
        ((o != null) ? o.getClass().getName() : "null"));
  }

  public void load() {
    super.load();

    // create the global registry
    GlobalRegistry globReg;
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
      globReg = new NamingServerRegistry(rootDir);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }

    try {
      // create and configure the server
      this.servEng = createServer(serverClassname, serverArg);
      configureServer(globReg);

      // create and advertise our service
      this.rootSP = new RootServletServiceProviderImpl();
      sb.addService(ServletService.class, rootSP);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public void start() {
    super.start();
    try {
      servEng.start();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  public void stop() {
    try {
      servEng.stop();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
    super.stop();
  }

  public void unload() {

    // revoke our service
    if (rootSP != null) {
      sb.revokeService(ServletService.class, rootSP);
      rootSP = null;
    }

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

  private void configureServer(
      GlobalRegistry globReg) throws Exception {
    servEng.configure(
        httpConfig,
        httpsConfig,
        debug);

    globReg.configure(httpConfig, httpsConfig);

    this.rootReg = 
      new RootServletRegistry(globReg);
    Servlet noNameServlet = 
      new RandomLocalRedirectServlet(rootReg);
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
    }
  }

}
