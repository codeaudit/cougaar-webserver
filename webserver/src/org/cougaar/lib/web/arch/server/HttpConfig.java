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

/**
 * HTTP configuration variables.
 */
public final class HttpConfig 
implements Serializable {

  private final int port;

  /**
   * Create a new HTTP configuration.
   * <p>
   * For now only the server's port is specified, and
   * "localhost" is assumed.  The port is typically 
   * 80 or 8080.
   */
  public HttpConfig(int port) {
    this.port = port;

    if (port <= 0) {
      throw new IllegalArgumentException(
          "Port must be >= zero");
    }
  }

  /**
   * Create a new HTTP config that is identical to the
   * given <tt>old</tt> HttpConfig, but replace the
   * <tt>old.getPort()</tt> with the given <tt>port</tt>.
   * <p>
   * This will be more useful if/when other HTTP config
   * details are added to HttpConfig.
   */
  public HttpConfig(
      HttpConfig old,
      int port) {
    this(port);
  }

  public int getPort() {
    return port;
  }

  /*

  //
  // We could (potentially) add all these properties.  The
  //   defaults listed below are from Tomcat 3.3:
  //

  boolean poolOn          =  true; // enable thread pool
  int     maxThreads      =   200;
  int     maxSpareThreads =    50;
  int     minSpareThreads =     4;
  int     backlog         =   100; // connections
  boolean tcpNoDelay      = false;
  int     soLinger        =   100; // millis
  int     soTimeout       =    -1; // disabled
  int     serverSoTimeout =  1000; // millis

  //
  // Adding all these properties might tie us to a particular 
  //   servlet-server implementation.  We'll need to consider
  //   them one-by-one.
  //

  */

  public String toString() {
    return 
      "(localhost:"+getPort()+")";
  }
}
