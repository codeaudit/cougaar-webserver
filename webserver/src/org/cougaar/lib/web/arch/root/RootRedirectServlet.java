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
package org.cougaar.lib.web.arch.root;

import java.io.IOException;
import java.net.URI;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A simple <code>Servlet</code> that uses the 
 * <code>GlobalRegistry</code> and the basic HTTP redirect
 * error code to redirect client requests to the appropriate
 * (remote) host.
 * <p>
 * In the future more complex redirection could be used, 
 * including proxies and tunnelling.
 */
public class RootRedirectServlet 
implements Servlet {

  protected final GlobalRegistry globReg;
  protected final Servlet unknownNameServlet;
  protected final long timeout;

  public RootRedirectServlet(
      GlobalRegistry globReg,
      Servlet unknownNameServlet,
      long timeout) {
    this.globReg = globReg;
    this.unknownNameServlet = unknownNameServlet;
    this.timeout = timeout;

    // null-check
    if (globReg == null) {
      throw new NullPointerException();
    } else if (unknownNameServlet == null) {
      throw new NullPointerException();
    }
  }

  public void service(
      ServletRequest req,
      ServletResponse res) throws ServletException, IOException {

    HttpServletRequest httpReq;
    HttpServletResponse httpRes;
    try {
      httpReq = (HttpServletRequest)req;
      httpRes = (HttpServletResponse)res;
    } catch (ClassCastException cce) {
      // not an HTTP request?
      throw new ServletException("non-HTTP request or response");
    }

    redirect(httpReq, httpRes);
  }

  /**
   * Utility method for redirect.
   */
  public final void redirect(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // get the "/$[~]encName[/.*]"
    String path = req.getRequestURI();
    // assert ((path != null) && (path.startsWith("/$")))
    int startIdx = 2; 
    if (startIdx < path.length()) {
      char ch = path.charAt(startIdx);
      if (ch == '~') {
        startIdx++;
      } else if (
          (ch == '%') &&
          path.regionMatches(startIdx + 1, "7E", 0, 2)) {
        // wget encodes "~" as "%7E"
        startIdx += 3;
      }
    }
    int sepIdx = path.indexOf('/', startIdx);
    if (sepIdx < 0) {
      sepIdx = path.length();
    }
    String encName = path.substring(startIdx, sepIdx);

    String scheme = req.getScheme();

    // find the URI for this url-encoded name
    URI uri;
    try {
      uri = globReg.get(encName, scheme, timeout);
    } catch (Exception e) {
      // generate error response
      res.sendError(
          HttpServletResponse.SC_NOT_FOUND, 
          "Unable to lookup Agent name: \""+encName+"\": "+
          e.getMessage());
      return;
    }

    if (uri == null) {
      // possible protocol switch (e.g. https to http)
      //
      // delegate to the unknown-name servlet
      unknownNameServlet.service(req, res);
      return;
    }
    String host = uri.getHost();
    int port = uri.getPort();

    // check for slow white pages update
    //
    // redirect loops are still possible but at least we catch the
    // self-loop case.
    String localHost = req.getServerName();
    int localPort = req.getServerPort();
    if ((host == null ?
          localHost == null : 
          host.equals(localHost)) &&
        (port == localPort)) {
      // generate error response
      res.sendError(
          HttpServletResponse.SC_NOT_FOUND, 
          "Servlet server "+localHost+":"+localPort+
          " does not contain agent \""+encName+
          "\".  The local white pages cache apparently contains"+
          " a stale or invalid entry: "+uri+
          ".  Please select a different host:port starting point"+
          " or try again later.");
      return;
    }

    String queryString = req.getQueryString();

    // create the new location string
    String location = 
      scheme+"://"+
      host+":"+port+
      path+
      ((queryString != null) ?
       ("?"+queryString) :
       (""));

    // encode for redirect -- typically a no-op
    location = res.encodeRedirectURL(location);

    // redirect the request to the remote location
    res.sendRedirect(location);
  }

  //
  // other Servlet methods
  //

  public void init(ServletConfig config) {
    // ignore
  }
  public ServletConfig getServletConfig() {
    // never called
    return null;
  }
  public String getServletInfo() {
    return "root-remote-redirect";
  }
  public void destroy() {
    // ignore
  }

}
