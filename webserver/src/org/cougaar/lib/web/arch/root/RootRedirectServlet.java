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

import org.cougaar.lib.web.arch.util.DummyServletConfig;

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
    return DummyServletConfig.getInstance();
  }
  public String getServletInfo() {
    return "root-remote-redirect";
  }
  public void destroy() {
    // ignore
  }

}
