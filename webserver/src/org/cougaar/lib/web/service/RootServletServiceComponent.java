/*
 * <copyright>
 *  
 *  Copyright 2000-2004 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * </copyright>
 */
package org.cougaar.lib.web.service;

import java.io.File;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;

import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.node.NodeControlService;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.root.GlobalRegistry;
import org.cougaar.lib.web.arch.root.RootRedirectServlet;
import org.cougaar.lib.web.arch.root.RootServlet;
import org.cougaar.lib.web.arch.root.RootServletRegistry;
import org.cougaar.lib.web.arch.server.ServletEngine;
import org.cougaar.util.Parameters;

/**
 * A node component that advertises the root-level
 * <code>RootServletService</code>, which is used by the agent-level
 * <code>ServletService</code> providers.
 * <p>
 * For example, node "N" creates the new root-level
 * RootServletService for all agents.  Agent "A" obtains the
 * RootServletService, registers itself as "/$A/", and advertises
 * the agent's internal ServletService.
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
 * @property org.cougaar.lib.web.redirect.timeout
 *   Timeout in millseconds for "/$" remote redirect lookups, where
 *   0 indicates no timeout.  Defaults to 0.
 * 
 * @property org.cougaar.lib.web.server.classname
 *   Classname for ServletEngine.  Defaults to Tomcat.
 *
 * @property org.cougaar.lib.web.server.arg
 *   Argument for ServletEngine.  Defaults to Tomcat path.
 *
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
 * @property org.cougaar.lib.web.http.acceptCount
 *   HTTP ServerSocket backlog.  Defaults to a server default.
 *
 * @property org.cougaar.lib.web.https.port
 *   The base integer port for the HTTPS server, which defaults to
 *   -1.  The most common value is 8400.  If a negative number is 
 *   passed then HTTPS listening is disabled.  Also see the 
 *   "org.cougaar.lib.web.scanRange" property.
 *
 * @property org.cougaar.lib.web.https.acceptCount
 *   HTTPS ServerSocket backlog.  Defaults to a server default.
 *
 * @property org.cougaar.lib.web.https.clientAuth
 *   Used to enable HTTPS client-authentication.  Defaults to
 *   false.
 *
 * @property org.cougaar.lib.web.https.factory
 *   Optional classname for factory used to create HTTPS (SSL) 
 *   server sockets.  Defaults to a standard SSL socket factory.
 *
 * @property org.cougaar.lib.web.https.keystore
 *   Optional HTTPS keystore.  Prefer "cougaar.rc" entry
 *   for "org.cougaar.web.keystore=FILENAME".
 *
 * @property org.cougaar.lib.web.https.keypass
 *   Optional HTTPS keystore.  Prefer "cougaar.rc" entry
 *   for "org.cougaar.web.keypass=PASSWORD".
 * </pre>
 *
 * @see RootServletService
 */
public class RootServletServiceComponent 
extends org.cougaar.util.GenericStateModelAdapter
implements Component 
{
  private static final String PROPERTY_PREFIX =
    "org.cougaar.lib.web.";

  private LoggingService log;

  private String localNode;

  // used to create the "rootReg"
  private WhitePagesService wp;

  private ServiceBroker sb;

  // from initialize
  private List initList;
  private long redirectTimeout;
  private String serverClassname;
  private Object serverArg;
  private int scanRange;

  private Map config;
  private int initHttpPort;
  private int initHttpsPort;

  private GlobalRegistry globReg;
  private ServiceProvider rootSP;
  private ServletEngine servEng;
  private ServletRegistry rootReg;

  // actual HTTP/HTTPS ports that were used, in case the 
  // initial ports (initHttp[s]Port) are already in use.
  private int usedHttpPort;
  private int usedHttpsPort;

  // ignore "setServiceBroker", we want the node-level service broker

  public void setNodeControlService(NodeControlService ncs) {
    if (ncs == null) {
      // Revocation
    } else {
      this.sb = ncs.getRootServiceBroker();
    }
  }

  public void setParameter(Object o) {
    if (o instanceof List) {
      this.initList = (List) o;
    } else if (o != null) {
      throw new IllegalArgumentException(
          "Invalid non-List parameter: "+
          o.getClass().getName());
    }
  }

  private void configureParameters() {

    // Map<String, String>
    Map m = new HashMap(13);

    String cip =
      System.getProperty("org.cougaar.install.path", ".");

    // set defaults
    m.put("debug", Boolean.toString(log.isDebugEnabled()));
    m.put("redirect.timeout", "0");
    m.put("scanRange", "100");
    m.put(
        "server.classname", 
        "org.cougaar.lib.web.tomcat.TomcatServletEngine");
    m.put(
        "server.arg",
        cip+File.separator+"webtomcat"+File.separator+"data");
    m.put("https.keyname", "tomcat");
    m.put("http.port", "8800");
    m.put("https.port", "-1");

    // override with init parameters
    if (initList != null) {
      for (Iterator iter = initList.iterator();
          iter.hasNext();
          ) {
        String s = (String) iter.next();
        if (s != null) {
          int sep = s.indexOf('=');
          String key;
          String value;
          if (sep >= 0) {
            key = s.substring(0, sep);
            value = s.substring(sep+1);
          } else {
            key = s;
            value = null;
          }
          m.put(key, value);
        }
      }
      this.initList = null;
    }

    // override with system properties
    Properties sysProps = 
      SystemProperties.getSystemPropertiesWithPrefix(
          PROPERTY_PREFIX);
    if (sysProps != null) {
      for (Enumeration en = sysProps.propertyNames();
          en.hasMoreElements();
          ) {
        String key = (String) en.nextElement();
        String value = sysProps.getProperty(key);
        key = key.substring(PROPERTY_PREFIX.length());
        m.put(key, value);
      }
    }
    
    // override with "cougaar.rc"
    String serverKeystore = 
      Parameters.findParameter("org.cougaar.web.keystore");
    if (serverKeystore != null) {
      // keystore is relative to "$org.cougaar.install.path"
      serverKeystore = cip+File.separator+serverKeystore;
      m.put("https.keystore", serverKeystore);
    }
    String serverKeypass = 
      Parameters.findParameter("org.cougaar.web.keypass");
    if (serverKeypass != null) {
      m.put("https.keypass", serverKeypass);
    }

    if (!m.containsKey("https.trustKeystore")) {
      // default trustKeystore is the keystore
      m.put("https.trustKeystore", serverKeystore);
    }

    // extract our parameters
    this.redirectTimeout =
      Long.parseLong((String) m.get("redirect.timeout"));
    this.serverClassname = (String) m.get("server.classname");
    this.serverArg = (String) m.get("server.arg");
    this.scanRange = Integer.parseInt((String) m.get("scanRange"));
    this.initHttpPort = 
      Integer.parseInt((String) m.get("http.port"));
    this.initHttpsPort = 
      Integer.parseInt((String) m.get("https.port"));

    if (log.isDebugEnabled()) {
      log.debug("Config: "+m);
    }

    // save the server options
    config = Collections.unmodifiableMap(m);
  }

  public void load() {
    super.load();

    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);
    if (log == null) {
      log = LoggingService.NULL;
    }

    // which agent are we in?
    NodeIdentificationService nis = (NodeIdentificationService)
      sb.getService(this, NodeIdentificationService.class, null);
    localNode = nis.getMessageAddress().getAddress();
    sb.releaseService(this, NodeIdentificationService.class, nis);

    configureParameters();

    // create the global registry
    try {
      // get the white pages service
      wp = (WhitePagesService)
        sb.getService(this, WhitePagesService.class, null);
      if (wp == null && log.isWarnEnabled()) {
        log.warn(
            "Root servlet-service unable to"+
            " obtain WhitePagesService");
      }

      // create a server registry
      this.globReg = new NamingServerRegistry(wp);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to create naming registry", e);
    }

    try {
      // create and configure the server
      this.servEng = createServer(serverClassname, serverArg);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to create server", e);
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Root server ("+servEng+") is running");
    }

    try {
      // start the server
      startServer();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to start servlet server", e);
    }

    try {
      // configure the server
      configureRootServlet();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to create server", e);
    }

    try {
      // configure the reg, which alters the rootReg
      globReg.configure(usedHttpPort, usedHttpsPort);
    } catch (RuntimeException re) {
      servEng.stop();
      throw re;
    } catch (Exception e) {
      servEng.stop();
      throw new RuntimeException(
          "Unable to register in the name server"+
          " (http="+usedHttpPort+", https="+usedHttpsPort+")",
          e);
    }

    // create and advertise our service
    this.rootSP = new RootServletServiceProviderImpl();
    sb.addService(RootServletService.class, rootSP);
  }

  public void unload() {

    try {
      // revoke our service
      if (rootSP != null) {
        sb.revokeService(RootServletService.class, rootSP);
        rootSP = null;
      }

      servEng.stop();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to stop server", e);
    }

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

  // maybe refactor this into a ServletEngineService with a standard
  // Tomcat-specific ServiceProvider implementation.
  private static ServletEngine createServer(
      String classname,
      Object arg) throws Exception {
    if (classname == null) {
      throw new IllegalArgumentException(
          "Server classname not specified");
    }
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

    Servlet welcomeServlet = new WelcomeServlet();
    Servlet agentsServlet = new AgentsServlet(rootReg, globReg);
    Servlet unknownNameServlet = 
      new UnknownRootNameServlet(rootReg);
    Servlet redirectServlet = 
      new RootRedirectServlet(
          globReg,
          unknownNameServlet,
          redirectTimeout);
    RootServlet rootServlet = 
      new RootServlet(
          rootReg, 
          localNode,
          welcomeServlet,
          agentsServlet,
          redirectServlet);

    servEng.setGateway(rootServlet);
  }

  /**
   * Start the server, scan the port range based upon the
   * "org.cougaar.lib.web.scanRange" property.
   * <p>
   * See the "@property" javadocs at the top of this file.
   */
  private void startServer() throws Exception {

    // validate our parameters
    if (scanRange < 1) {
      throw new IllegalArgumentException(
          "Port scan range must be at least 1, not \""+
          scanRange+"\"");
    }
    if ((initHttpPort  > 0) &&
        (initHttpsPort > 0) &&
        (initHttpPort == initHttpsPort)) {
      throw new IllegalArgumentException(
          "HTTP port ("+initHttpPort+
          ") and HTTPS port ("+initHttpsPort+
          ") must be different!");
    }

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
        throw new BindException(msg);
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

      servEng.configure(
          httpPort,
          httpsPort,
          config);

      try {
        servEng.start();
        break;
      } catch (BindException be) {
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
   * Service provider for our <code>RootServletService</code>.
   */
  private class RootServletServiceProviderImpl
  implements ServiceProvider {

    public Object getService(
        ServiceBroker sb, 
        Object requestor, 
        Class serviceClass) {
      // create a new service instance
      if (serviceClass == RootServletService.class) {
        return new RootServletServiceImpl();
      } else {
        throw new IllegalArgumentException(
            "RootServletService does not provide a service for: "+
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
    implements RootServletService {

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
