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
package org.cougaar.lib.web.tomcat;

import java.io.File;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.connector.http.HttpConnector;
import org.apache.catalina.Engine;
import org.apache.catalina.Loader;
import org.apache.catalina.net.SSLServerSocketFactory;
import org.apache.catalina.net.ServerSocketFactory;
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
      this.debug = 
        "true".equals(options.get("debug"));
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

    if (options != null) {
      factoryClassname = (String) 
        options.get("factory");
      // check for unknown option names?

      /*
      // other options we may want to add:
      minProcessors="5"
      maxProcessors="75"
      enableLookups="true"
      acceptCount="10"
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

    addEndpoint(port, null, fact);
  }

  /**
   * Adds an HTTP socket connector to the server.
   */
  public void addEndpoint(
      int port, InetAddress address,
      ServerSocketFactory fact) {
    HttpConnector ctr = new HttpConnector();
    if (fact != null) ctr.setFactory(fact);
    if (address != null) ctr.setAddress(address.getHostAddress());
    ctr.setPort(port);
    ctr.setEnableLookups(false);
    if (debug) ctr.setDebug(30);
    getService().addConnector(ctr);
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

    addSecureEndpoint(port, address, fact);
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
    getService().addConnector(ctr);
  }

}
