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
package org.cougaar.lib.web.arch.server;

import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

/**
 * API for the server that will run the single <code>Servlet</code>
 * for this application.
 * <p>
 * Note that the user code or the ServletEngine may generate custom
 * error pages.  "Internet Explorer" users should disable the
 * "friendly error pages" option:<pre>
 *    tools-&gt;Internet Options-&gt;Advanced-&gt;Show Friendly HTTP error messages == UNCHECKED
 * </pre>.
 */
public interface ServletEngine {

  //
  // Must implement a "ServletEngine(String[])" constructor.
  //

  /**
   * Set the HTTP and HTTPS (SSL) configuration -- this can only 
   * be called when <tt>(isRunning() == false)</tt>.
   *
   * @param serverOptions an option map of server configuration 
   *    parameters, which are implementation specific.
   * @param httpPort the HTTP port, or -1 if HTTP is disabled
   * @param httpsOptions an optional map of HTTP configuration 
   *    parameters, which are implementation specific.
   * @param httpsPort the HTTPS port, or -1 if HTTP is disabled
   * @param httpsOptions an optional map of HTTPS configuration 
   *    parameters, which are implementation specific.
   */
  public void configure(
      Map serverOptions,
      int httpPort, Map httpOptions,
      int httpsPort, Map httpsOptions) throws Exception;

  /**
   * Start the server.
   *
   * @see #isRunning()
   * @see #stop()
   */
  public void start() throws Exception;

  /**
   * @return true if the server has been started and not stopped yet.
   */
  public boolean isRunning();

  /**
   * Set the single <code>Servlet</code> that will handle <b>all</b>
   * service requests for the running server.
   * <p>
   * Even though this is defined as <tt>setGateway(Servlet)</tt>, it's
   * expected that all calls to<pre>
   *  <tt>Servlet.service(ServletRequest,ServletResponse)</tt>
   * actually pass
   *  <tt>Servlet.service(HttpServletRequest,HttpServletResponse)</tt>
   * </pre>, since the ServletEngine is responsible for formatting
   * the HTTP and HTTPS request.
   */
  public void setGateway(Servlet s) throws ServletException;

  /**
   * Kill the server.
   */
  public void stop();

}
