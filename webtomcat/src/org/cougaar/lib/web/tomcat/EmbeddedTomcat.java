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

import java.net.InetAddress;
import java.io.File;
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
   * Returns a reference to the Server object. 
   *
   * @throws IllegalStateException If the configuration file hasn't been read.
   * @see readConfigFile
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
   * @see readConfigFile
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
   * @see readConfigFile
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
   *
   * @param port The TCP port number to bind to
   * @param address The IP address to use. May be null to refer to the
   *                default address.
   */
  public void addEndpoint(int port, InetAddress address) {
    HttpConnector ctr = new HttpConnector();
    if (address != null) ctr.setAddress(address.getHostAddress());
    ctr.setPort(port);
    ctr.setEnableLookups(false);
    if (debug) ctr.setDebug(30);
    getService().addConnector(ctr);
  }

  /**
   * Adds an HTTPS socket connector to the server.
   *
   * @param port The TCP port number to bind to
   * @param address The IP address to use. May be null to refer to the
   *                default address.
   * @param keystore The file name of the keystore to use. null means that
   *                 it should use the default keystore
   * @param keypass  The password for the given keystore file.
   * @param clientAuth True if you want to require client authentication.
   */
  public void addSecureEndpoint(int port, InetAddress address,
                                String keystore, String keypass,
                                boolean clientAuth) {
    HttpConnector ctr = new HttpConnector();
    SSLServerSocketFactory fact = new SSLServerSocketFactory();
    if (keystore != null) {
      fact.setKeystoreFile(keystore);
      fact.setKeystorePass(keypass);
    }
    if (clientAuth) {
      fact.setClientAuth(true);
    }
    ctr.setFactory(fact);
    if (address != null) ctr.setAddress(address.getHostAddress());
    ctr.setPort(port);
    ctr.setEnableLookups(false);
    ctr.setScheme("https");
    ctr.setSecure(true);
    if (debug) ctr.setDebug(30);
    getService().addConnector(ctr);
  }

  /**
   * Set the debug flag for this class
   */
  void setDebug(boolean debug) {
    this.debug = debug;
  }
}
