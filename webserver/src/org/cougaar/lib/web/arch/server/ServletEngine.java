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
package org.cougaar.lib.web.arch.server;

import java.io.IOException;
import java.net.BindException;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

/**
 * API for the server that will run the single <code>Servlet</code>
 * for this application, for example the
 * <code>TomcatServletEngine</code>.
 * <p>
 * Note that the user code or the ServletEngine may generate custom
 * error pages.  "Internet Explorer" users should disable the
 * "friendly error pages" option:<pre>
 *    tools-&gt;Internet Options-&gt;Advanced-&gt;Show Friendly HTTP error messages == UNCHECKED
 * </pre>.
 *
 * @see #setGateway 
 */
public interface ServletEngine {

  //
  // Must implement a "ServletEngine(String[])" constructor.
  //

  /**
   * Set the HTTP and HTTPS (SSL) configuration -- this can only 
   * be called when <tt>(isRunning() == false)</tt>.
   *
   * @param httpPort the HTTP port, or -1 if HTTP is disabled
   * @param httpsPort the HTTPS port, or -1 if HTTP is disabled
   * @param options an optional map of configuration options
   *    that are implementation specific (e.g. "http.acceptCount").
   */
  public void configure(int httpPort, int httpsPort, Map options);

  /**
   * Start the server.
   *
   * @see #isRunning()
   * @see #stop()
   *
   * @throws BindException if the either the HTTP or HTTPS port is
   * already in use.  The client can then optionally call "configure"
   * with different port(s) and try another start.
   * @throws IOException some other server exception occurred.
   */
  public void start() throws BindException, IOException;

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
