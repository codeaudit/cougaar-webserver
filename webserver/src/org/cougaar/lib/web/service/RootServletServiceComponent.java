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

  /**
   * The parameter is an Object[] of:<pre>
   *   String servlet engine classname
   *   Object servlet engine configuration info
   *   Integer HTTP  port, or null to disable HTTP
   *   Integer HTTPS port, or null to disable HTTPS
   *   String public keystore for trusted clients 
   *   String private keystore for the server certificate
   *   String private password for the server keystore
   *   String private keyname for the server certificate
   *   Boolean enable/disable client authentication
   * </pre>.
   * <p>
   * If the keystore file name does not start with "/" then
   * the ${org.cougaar.install.path}+"/" is prefixed.
   * <p>
   * Magic ordering of this String[] -- need to fix
   * to make this cleaner, but still want to keep the API
   * simple.  Maybe a "java.util.Properties"?
   */
  public void setParameter(Object o) {
    if (!(o instanceof Object[])) {
      throw new IllegalArgumentException(
          "Expecting a \"Object[]\" parameter, not \""+
          ((o != null) ? o.getClass().getName() : "null")+
          "\"");
    }
    Object[] oa = (Object[]) o;

    // get all these parameters:
    int httpPort;
    int httpsPort;
    String trustKeystore;
    String serverKeystore;
    String serverKeyname;
    String serverKeypass;
    boolean clientAuth;
    try {
      this.serverClassname = (String) oa[0];
      this.serverArg = oa[1];
      httpPort = 
        ((oa[2] != null) ? (((Integer) oa[2]).intValue()) : (-1));
      httpsPort = 
        ((oa[3] != null) ? (((Integer) oa[3]).intValue()) : (-1));
      trustKeystore = (String) oa[4];
      serverKeystore = (String) oa[5];
      serverKeyname = (String) oa[6];
      serverKeypass = (String) oa[7];
      clientAuth = 
        ((oa[8] != null) && ((Boolean) oa[8]).booleanValue());
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Illegal "+this.getClass().getName()+" parameter:"+
          e.getMessage());
    }

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

    // this is constant:
    InetAddress localAddr;
    try {
      localAddr = InetAddress.getLocalHost();
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to get localhost address: "+e.getMessage());
    }

    // create the HTTP config
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
          serverKeyname,
          serverKeypass,
          trustKeystore)) :
       (null));
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
