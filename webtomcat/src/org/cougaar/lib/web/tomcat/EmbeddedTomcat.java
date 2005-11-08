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
package org.cougaar.lib.web.tomcat;

import java.net.InetAddress;
import java.util.Map;

import org.apache.catalina.Connector;
import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.http.HttpConnector;
import org.apache.catalina.net.SSLServerSocketFactory;
import org.apache.catalina.net.ServerSocketFactory;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.util.xml.XmlMapper;

/**
 * This class wraps the org.apache.catalina.startup.Catalina class to
 * follow similar behavior as Tomcat 3.3's EmbededTomcat class. The
 * most important functionality that it handles is reading the server.xml
 * file and then allowing the caller access to the objects. In specific,
 * the Connectors needed to be configurable dynamically.
 *
 * This class inherits from Catalina only to get access to the server objects.
 */
public class EmbeddedTomcat extends Catalina {

  /**
   * Default constructor
   */
  public EmbeddedTomcat() {
  }

  /**
   * Configure the server.
   * <p>
   * Current valid options:
   * <ul>
   *  <li>"debug"<br>
   *      Optional debug flag, which defaults to "false".
   *      </li><p>
   * </ul>
   *
   * @param options Map of optional parameters
   */
  public void configure(Map options) {
    if (options != null) {
      // DISABLED Tomcat logger debugging:
      // See Cougaar bug 1402.
      // Fix postponed to after 9.4 release.
      //
      //this.debug = "true".equals(options.get("debug"));
    }
  }

  /**
   * Returns a reference to the Server object. 
   *
   * @throws IllegalStateException If the configuration file hasn't been read.
   * @see #readConfigFile
   */
  public Server getServer() {
    if (server == null) 
      throw new IllegalStateException("The configuration file has not been read");
    return server;
  }

  /**
   * Returns a reference to the Service. There may only be one service in
   * the configuration file.
   *
   * @throws IllegalStateException If the configuration file hasn't been read.
   * @see #readConfigFile
   */
  public Service getService() {
    Service[] services = getServer().findServices();
    if (services == null || services.length != 1) {
      throw new IllegalStateException("There must be one and only one Service with in the Server tag");
    }
    
    return services[0];
  }

  /**
   * Returns the Engine object. There may only be one Engine.
   *
   * @throws IllegalStateException If the configuration file hasn't been read.
   * @see #readConfigFile
   */
  public Engine getEngine() {
    Container contEngine = getService().getContainer();
    if (contEngine == null || !(contEngine instanceof Engine)) {
      throw new IllegalStateException("There must be one Engine tag within the Service tag");
    }
    return (Engine)contEngine;
  }

  /**
   * Sets the catalina.home and catalina.base paths to the given String
   */
  public void setInstall(String installPath) {
    System.setProperty("catalina.home",installPath);
    System.setProperty("catalina.base",installPath);
  }

  /**
   * Reads the xml configuration file given. This must be called before
   * the server is started via embeddedStart.
   */ 
  public void readConfigFile(String configFile) throws Exception {
    this.configFile = configFile;
    XmlMapper mapper = createStartMapper();
    mapper.readXml(configFile(), this);
  }

  /**
   * Starts the web server.
   */
  public void embeddedStart() throws LifecycleException {
    server.initialize();
    if (server instanceof Lifecycle) {
      ((Lifecycle)server).start();
    }
  }

  /**
   * Stops the web server.
   */
  public void embeddedStop() throws LifecycleException {
    if (server instanceof Lifecycle) {
      ((Lifecycle)server).stop();
    }
  }

  /**
   * Adds an HTTPConnector to the server.
   * <p>
   * Current valid options:
   * <ul>
   *  <li>"factory"<br>
   *      Optional classname for server socket factory, which 
   *      defaults to 
   *      "org.apache.catalina.net.DefaultServerSocketFactory"
   *      </li><p>
   * </ul>
   *
   * @param port The TCP port number to bind to
   * @param options Map of optional parameters
   */
  public void addEndpoint(
      int port,
      Map options) {
    String factoryClassname = null;
    int acceptCount = 10;

    if (options != null) {
      factoryClassname = (String) options.get("factory");
      String ac = (String) options.get("acceptCount");
      if (ac != null) acceptCount = Integer.parseInt(ac);
      // check for unknown option names?

      /*
      // other options we may want to add:
      minProcessors="5"
      maxProcessors="75"
      enableLookups="true"
      debug="0"
      connectionTimeout="60000"
      */
    }

    ServerSocketFactory fact = null;
    if (factoryClassname != null) {
      try {
        Class cl = Class.forName(factoryClassname);
        fact = (ServerSocketFactory) cl.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(
            "Invalid server socket factory: "+factoryClassname, e);
      }
    }

    addEndpoint(port, null, fact, acceptCount);
  }

  /**
   * Adds an HTTP socket connector to the server.
   */
  public void addEndpoint(
      int port,
      InetAddress address,
      ServerSocketFactory fact,
      int acceptCount) {
    HttpConnector ctr = new HttpConnector();
    if (acceptCount >= 0) ctr.setAcceptCount(acceptCount);
    if (fact != null) ctr.setFactory(fact);
    if (address != null) ctr.setAddress(address.getHostAddress());
    ctr.setPort(port);
    ctr.setEnableLookups(false);
    if (debug) ctr.setDebug(30);

    getService().addConnector(ctr);

    setRedirectPort(ctr);
  }

  /**
   * Adds an HTTPS socket connector to the server.
   * <p>
   * Note, if the factory is not specified, then Tomcat
   * imposes these restrictions:
   * <ul>
   *   <li>The certificate name must be "tomcat".
   *       </li><p>
   *   <li>The keystore and the truststore must be the
   *       same file.
   *       </li><p>
   * </ul>
   * <p>
   * Current valid options:
   * <ul>
   *  <li>"factory"<br>
   *      Optional classname for server socket factory, which 
   *      defaults to 
   *      "org.apache.catalina.net.SSLServerSocketFactory"
   *      </li><p>
   *  <li>"keystore"<br>
   *      Optional file name for the certificate keystore.
   *      </li><p>
   *  <li>"keypass"<br>
   *      Optional password for the certificate keystore.
   *      </li><p>
   *  <li>"clientAuth"<br>
   *      If the value is "true", then client authentication
   *      is enabled.
   *      </li><p>
   * </ul>
   *
   * @param port The TCP port number to bind to
   * @param options Map of optional parameters
   */
  public void addSecureEndpoint(
      int port,
      Map options) {
    String factoryClassname = null;
    String keystore = null;
    String keypass = null;
    boolean clientAuth = false;

    if (options != null) {
      factoryClassname = (String) 
        options.get("factory");
      keystore = (String) 
        options.get("keystore");
      keypass = (String) 
        options.get("keypass");
      clientAuth =
        "true".equals(
            options.get("clientAuth"));
      // also see "addEndpoint(..)" options
    }

    ServerSocketFactory fact;
    if (factoryClassname == null) {
      if ((keystore == null) ||
          (keypass == null)) {
        throw new IllegalArgumentException(
            "Must specify a keystore and keypass when using "+
            "HTTPS and the default server socket factory");
      }
      fact = new SSLServerSocketFactory();
    } else {
      try {
        Class cl = Class.forName(factoryClassname);
        fact = (ServerSocketFactory) cl.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(
            "Invalid server socket factory: "+factoryClassname, e);
      }
    }

    // replace with reflective parameter setting?
    if (fact instanceof SSLServerSocketFactory) {
      SSLServerSocketFactory sslfact = 
        (SSLServerSocketFactory) fact;
      if (keystore != null) {
        sslfact.setKeystoreFile(keystore);
      }
      if (keypass != null) {
        sslfact.setKeystorePass(keypass);
      }
      if (clientAuth) {
        sslfact.setClientAuth(true);
      }
      // protocol?
    }

    addSecureEndpoint(port, null, fact);
  }

  /**
   * Adds an HTTPS socket connector to the server.
   */
  public void addSecureEndpoint(int port, InetAddress address,
                                ServerSocketFactory fact) {
    HttpConnector ctr = new HttpConnector();
    if (fact != null) ctr.setFactory(fact);
    if (address != null) ctr.setAddress(address.getHostAddress());
    ctr.setPort(port);
    ctr.setEnableLookups(false);
    ctr.setScheme("https");
    ctr.setSecure(true);
    if (debug) ctr.setDebug(30);

    setRedirectPort(ctr);

    getService().addConnector(ctr);

  }

  /**
   * Sets the redirection port all HTTP connections if this is an
   * HTTPS connection or the redirection port of this connection
   * if an HTTPS connection exists and this is an HTTP connection.
   *
   * @param conn The HTTP to set the redirection port or the HTTPS
   *             connection to set all other connections' redirection ports.
   */
  protected void setRedirectPort(HttpConnector conn) {
    Connector[] connectors = getService().findConnectors();
    
    if (connectors == null) {
      return; // there are no connectors
    }
    
    for (int i = 0; i < connectors.length; i++) {
      if (!(connectors[i] instanceof HttpConnector)) {
	continue; // don't do anything to non HTTP connectors
      }
      HttpConnector httpConn = (HttpConnector) connectors[i];
      
      if (httpConn.getSecure()) {
	if (!conn.getSecure()) {
	  // found the HTTPS connection
	  conn.setRedirectPort(httpConn.getPort());
	  return;
	}
      } else {
	if (conn.getSecure()) {
	  httpConn.setRedirectPort(conn.getPort());
	}
      }
    }
  }
}
