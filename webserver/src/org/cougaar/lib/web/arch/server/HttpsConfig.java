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
package org.cougaar.lib.web.arch.server;

import java.io.Serializable;
import java.net.InetAddress;

/**
 * HTTPS (SSL) configuration.
 * <p>
 * Consider <b>SECURITY</b> when extracting data from 
 * this configuration.  For example, don't print the
 * server's password to standard-out.  Additionally the 
 * keypass is not serialized.
 *
 * @see HttpConfig
 */
public final class HttpsConfig
implements Serializable {

  private final HttpConfig httpConfig;
  private final boolean clientAuth;
  private final String serverKeystore;
  private final String serverKeyname;
  private final String trustKeystore;

  // this are transient for SECURITY reasons
  private transient final String serverKeypass;

  /**
   * Create a new HTTPS configuration.
   * <p>
   * An <code>HttpConfig</code> must be passed for
   * basic server information, such as the host and port.
   * The port must be different than the HTTP port -- 
   * 443 or 8443 are typically used.
   * <p>
   * The server requires a keystore, including:
   * <ul>
   *   <li>the keystore file name</li>
   *   <li>the password for access to the keystore</li>
   *   <li>the name for the server certificiate</li>
   * </ul>
   * <p>
   * If <tt>(clientAuth == false)</tt> then only server-side 
   * SSL will be used, and <tt>trustKeystore</tt> is ignored.
   * <p>
   * If <tt>(clientAuth == true)</tt> then the server will do 
   * two-way client-server authentication for client 
   * authorization.  The <tt>trustKeystore</tt> must also be 
   * specified if <tt>(clientAuth == true)</tt> -- the
   * <tt>trustKeystore</code> is the file that contains the 
   * public certificates for trusted clients.
   *
   * @see HttpConfig
   */
  public HttpsConfig(
      HttpConfig httpConfig,
      boolean clientAuth,
      String serverKeystore,
      String serverKeypass,
      String serverKeyname,
      String trustKeystore) {
    this.httpConfig = httpConfig;
    this.clientAuth = clientAuth;
    this.serverKeystore = serverKeystore;
    this.serverKeypass = serverKeypass;
    this.serverKeyname = serverKeyname;
    this.trustKeystore = trustKeystore;

    if (httpConfig == null) {
      throw new NullPointerException(
          "Missing basic host:port information");
    }

    if ((serverKeystore == null) ||
        (serverKeypass == null) ||
        (serverKeyname == null)) {
      throw new NullPointerException(
          "Missing server keystore information");
    }

    if (clientAuth &&
        (trustKeystore == null)) {
      throw new NullPointerException(
          "Must specify a \"trustKeystore\" if "+
          "client-authentication is enabled");
    }
  }

  /**
   * Create a new HTTPS config that is identical to the
   * given <tt>old</tt> HttpsConfig, but replace the
   * <tt>old.getHttpConfig.getPort()</tt> with the given <tt>port</tt>.
   */
  public HttpsConfig(
      HttpsConfig old,
      int port) {
    this(
        new HttpConfig(old.httpConfig, port),
        old.clientAuth,
        old.serverKeystore,
        old.serverKeypass,
        old.serverKeyname,
        old.trustKeystore);
  }

  /**
   * This contains the basic host:port information.
   */
  public HttpConfig getHttpConfig() {
    return httpConfig;
  }

  public boolean getClientAuth() {
    return clientAuth;
  }

  public String getServerKeystore() {
    return serverKeystore;
  }

  /**
   * Don't expose this -- consider SECURITY.
   */
  public String getServerKeypass() {
    return serverKeypass;
  }

  public String getServerKeyname() {
    return serverKeyname;
  }

  public String getTrustKeystore() {
    return trustKeystore;
  }

  public String toString() {
    return 
      "HTTPS config {"+
      "\n  basic config:"+
      "\n"+httpConfig+
      "\n  client-auth: "+clientAuth+
      "\n  server-keystore: "+serverKeystore+
      "\n  server-keypass: <private>"+
      "\n  server-keyname: "+serverKeyname+
      "\n  trust-keystore: "+
      ((clientAuth) ? trustKeystore : "<N/A>")+
      "\n}";
  }
}
