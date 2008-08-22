/*
 * <copyright>
 *  
 *  Copyright 2000-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.engine;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.util.GenericStateModelAdapter;
import org.cougaar.util.Parameters;

/**
 * This component is a base class for {@link ServletEngineService} providers.
 * <p>
 * This class reads the parameters, scans for available ports, starts the
 * server, and advertises our service.
 * <p>
 * If HTTPS is enabled, the "cougaar.rc" must contain:<pre>
 *   org.cougaar.web.keystore=FILENAME
 *   org.cougaar.web.keypass=Password
 * </pre>
 * where the FILENAME is relative to the "$cougaar.install.path".
 * See {@link Parameters} for "cougaar.rc" documentation.
 * <p>
 * All the following parameters are also supported as prefix-free component
 * parameters, e.g. "http.port=1234".
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
 */
public abstract class AbstractServletEngine
extends GenericStateModelAdapter
implements Component 
{
  private static final String PROPERTY_PREFIX = "org.cougaar.lib.web.";

  protected ServiceBroker sb;

  protected LoggingService log;

  private List paramList;

  private boolean running;

  private Map namingEntries;

  private ServletEngineService ses;
  private ServletEngineRegistryService sers;
  private ServiceProvider ses_sp;

  //
  // abstract methods:
  //

  /**
   * Set the HTTP and HTTPS (SSL) configuration -- this is called before
   * {@link #startServer()}.
   *
   * @param httpPort the HTTP port, or -1 if HTTP is disabled
   * @param httpsPort the HTTPS port, or -1 if HTTP is disabled
   * @param options a Map of String keys to String values that specify
   *    additional server options (e.g. "http.acceptCount").
   */
  protected abstract void configure(int httpPort, int httpsPort, Map options);

  /**
   * Start the server.
   *
   * @throws BindException if the either the HTTP or HTTPS port is already in
   *   use.  The base class will then call "configure" with different port(s)
   *   and try to start again.
   * @throws IOException some other server exception occurred.
   */
  protected abstract void startServer() throws BindException, IOException;

  /**
   * Set the single <code>Servlet</code> that will handle <b>all</b> service
   * requests for the running server.
   * <p>
   * Even though this is defined as <tt>setGateway(Servlet)</tt>, all calls
   * to:<pre>
   *  <tt>Servlet.service(ServletRequest, ServletResponse)</tt>
   * must pass in the HTTP subclasses of these request/response interfaces:
   *  <tt>Servlet.service(HttpServletRequest, HttpServletResponse)</tt>
   * </pre>, since this is an HTTP/HTTPS server engine.
   */
  protected abstract void setGateway(Servlet s) throws ServletException;

  /**
   * Stop the server.
   * <p>
   * This is only called if {@link #startServer()} was called and didn't throw
   * an exception.
   */
  protected abstract void stopServer();

  //
  // implementation:
  //

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void setParameter(Object o) {
    if (!(o instanceof List)) {
      throw new IllegalArgumentException(
          "Invalid non-List parameter: "+o.getClass().getName());
    }
    this.paramList = (List) o;
  }

  protected Map parseParameters(List args) {
    // Map<String, String>
    Map m = new HashMap(13);

    // set defaults
    m.put("debug", Boolean.toString(log.isDebugEnabled()));
    m.put("redirect.timeout", "0");
    m.put("scanRange", "100");
    m.put("https.keyname", "tomcat");
    m.put("http.port", "8800");
    m.put("https.port", "-1");

    // override with init parameters
    for (int i = 0; i < args.size(); i++) {
      String s = (String) args.get(i);
      if (s == null) continue;
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
      // look for keystore in RUNTIME, SOCIETY, then INSTALL
      for (int i = 0; i < 2; i++) {
        String s = (i == 0 ? "runtime" : i == 1 ? "society" : "install");
        String base = SystemProperties.getProperty("org.cougaar."+s+".path");
        if (base != null) {
          String file = base+File.separator+serverKeystore;
          if ((new File(file)).isFile()) {
            serverKeystore = file;
            break;
          }
        }
      }
      if (serverKeystore != null) {
        m.put("https.keystore", serverKeystore);
      }
    }
    String serverKeypass = 
      Parameters.findParameter("org.cougaar.web.keypass");
    if (serverKeypass != null) {
      m.put("https.keypass", serverKeypass);
    }

    if (!m.containsKey("https.trustKeystore") && serverKeystore != null) {
      // default trustKeystore is the keystore
      m.put("https.trustKeystore", serverKeystore);
    }

    if (log.isDebugEnabled()) {
      log.debug("Config: "+m);
    }

    // save the server options
    return Collections.unmodifiableMap(m);
  }

  public void load() {
    super.load();

    // get services
    log = (LoggingService) sb.getService(this, LoggingService.class, null);

    // read config map
    List l = (paramList == null ? Collections.EMPTY_LIST : paramList);
    paramList = null;
    Map config = parseParameters(l);
    if (config == null) {
      config = Collections.EMPTY_MAP;
    }

    // configure and start the server
    final UsedPorts usedPorts;
    try {
      usedPorts = startServer(config);
    } catch (Exception e) {
      throw new RuntimeException("Unable to start servlet server", e);
    }
    running = true;

    // create and advertise our service
    ses = new ServletEngineService() {
      private Servlet gateway;
      public void setGateway(Servlet s) throws ServletException {
        this.gateway = s;
        AbstractServletEngine.this.setGateway(s);
      }
      public Map getNamingEntries() {
        return findOrMakeNamingEntries(usedPorts, gateway);
      }
      public String toString() {
        return AbstractServletEngine.this.toString();
      }
    };
    sers = (ServletEngineRegistryService)
      sb.getService(this, ServletEngineRegistryService.class, null);
    if (sers != null) {
      // share registry
      sers.add(ses);
    } else {
      // just us
      final Class cl = ServletEngineService.class;
      ses_sp = new ServiceProvider() {
        public Object getService(ServiceBroker sb, Object req, Class scl) {
          return (cl.isAssignableFrom(scl) ? ses : null);
        }
        public void releaseService(
            ServiceBroker sb, Object req, Class scl, Object svc) {
        }
      };
      sb.addService(cl, ses_sp);
    }
  }

  public void unload() {
    // remove/revoke our service
    if (ses != null) {
      if (sers != null) {
        sers.remove(ses);
        sers = null;
      } else if (ses_sp != null) {
        sb.revokeService(ServletEngineService.class, ses_sp);
        ses_sp = null;
      }
      ses = null;
    }

    // stop server
    if (running) {
      running = false;
      try {
        stopServer();
      } catch (Exception e) {
        throw new RuntimeException("Unable to stop server", e);
      }
    }

    // release the logger
    if (log != null) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }

    super.unload();
  }

  /**
   * Start the server, scan the port range based upon the
   * "org.cougaar.lib.web.scanRange" property.
   * <p>
   * See the "@property" javadocs at the top of this file.
   *
   * @return used ports
   */
  protected UsedPorts startServer(Map config) throws Exception {

    // extract our parameters
    int scanRange = 
      Integer.parseInt((String) config.get("scanRange"));
    int initHttpPort = 
      Integer.parseInt((String) config.get("http.port"));
    int initHttpsPort = 
      Integer.parseInt((String) config.get("https.port"));

    if (initHttpPort < 0 && initHttpsPort < 0) {
      return null;
    }

    // validate our parameters
    if (scanRange < 1) {
      throw new IllegalArgumentException(
          "Port scan range must be at least 1, not \""+scanRange+"\"");
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

      configure(httpPort, httpsPort, config);

      try {
        startServer();
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

    // success
    if (log.isInfoEnabled()) {
      log.info(
          "\nServer launched with: "+
          ((httpPort > 0) ? 
           ("\nHTTP : "+httpPort) :
           (""))+
          ((httpPort > 0) ? 
           ("\nHTTPS: "+httpsPort) :
           ("")));
    }
    return new UsedPorts(httpPort, httpsPort);
  }

  protected Map findOrMakeNamingEntries(UsedPorts usedPorts, Servlet gateway) {
    if (namingEntries == null) {
      namingEntries = Collections.EMPTY_MAP;
      Map m = makeNamingEntries(usedPorts, gateway);
      if (m != null && !m.isEmpty()) {
        namingEntries = Collections.unmodifiableMap(m);
      }
    }
    return namingEntries;
  }

  protected Map makeNamingEntries(UsedPorts usedPorts, Servlet gateway) {
    // get host:port info
    if (usedPorts == null) {
      return null;
    }
    int httpPort = usedPorts.getHttpPort();
    int httpsPort = usedPorts.getHttpsPort();
    if (httpPort < 0 && httpsPort < 0) {
      return null;
    }
    NodeIdentificationService nis = sb.getService(this, NodeIdentificationService.class, null);
    InetAddress localaddr = nis.getInetAddress();
    sb.releaseService(this, NodeIdentificationService.class, nis);
    String localhost;
    if (localaddr != null) {
      localhost = localaddr.getHostName();
    } else {
      throw new RuntimeException("Unable to get localhost address");
    }

    // get the optional base path from our gateway.
    //
    // This should match our servlet request "getContextPath()", but that
    // method is only available during a servlet call.  As a work-around,
    // we'll check for a custom "path" context parameter.
    String contextPath = "";
    if (gateway != null) {
      ServletConfig sc = gateway.getServletConfig();
      if (sc != null) {
        String s = sc.getInitParameter("path");
        if (s != null && s.length() > 0 && s.charAt(0) == '/') {
          contextPath = s;
        }
      }
    }

    // create our entries
    Map m = new HashMap();
    if (httpPort >= 0) {
      m.put(
          "http",
          URI.create(
            "http://"+localhost+
            (httpPort == 80 ? "" : (":"+httpPort))+
            contextPath));
    }
    if (httpsPort >= 0) {
      m.put(
          "https",
          URI.create(
            "https://"+localhost+
            (httpsPort == 443 ? "" : (":"+httpsPort))+
            contextPath));
    }
    return m;
  }

  protected static final class UsedPorts {
    private final int httpPort;
    private final int httpsPort;
    public UsedPorts(int httpPort, int httpsPort) {
      this.httpPort = httpPort;
      this.httpsPort = httpsPort;
    }
    public int getHttpPort() { return httpPort; }
    public int getHttpsPort() { return httpsPort; }
    public String toString() {
      return "(used-ports http="+httpPort+" https="+httpsPort+")";
    }
  }
}
