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
package org.cougaar.lib.web.arch.root;

import java.io.IOException;
import java.net.InetAddress;

import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.lib.web.arch.DummyServletConfig;

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

  public RootRedirectServlet(
      GlobalRegistry globReg,
      Servlet unknownNameServlet) {
    this.globReg = globReg;
    this.unknownNameServlet = unknownNameServlet;

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

    // get the "/$name[/.*]"
    String path = req.getRequestURI();
    // assert ((path != null) && (path.startsWith("/$")))
    int sepIdx = path.indexOf('/', 2);
    if (sepIdx < 0) {
      sepIdx = path.length();
    }
    String name = path.substring(2, sepIdx);

    // find the global entry for this name
    GlobalEntry ge;
    try {
      ge = globReg.find(name);
    } catch (Exception e) {
      // generate error response
      res.sendError(
          HttpServletResponse.SC_NOT_FOUND, 
          "Unable to lookup Agent name: \""+name+"\": "+
          e.getMessage());
      return;
    }

    if (ge == null) {
      // delegate to the unknown-name servlet
      unknownNameServlet.service(req, res);
      return;
    }


    String scheme = req.getScheme();

    InetAddress addr;
    int port;
    if ("https".equalsIgnoreCase(scheme)) {
      addr = ge.getHttpsAddress();
      port = ge.getHttpsPort();
    } else {
      // assert ("http".equalsIgnoreCase(scheme))
      addr = ge.getHttpAddress();
      port = ge.getHttpPort();
    }

    if ((addr == null) ||
        (port <= 0)) {
      // generate error response
      res.sendError(
          HttpServletResponse.SC_NOT_FOUND, 
          "Protocol \""+scheme+
          "\" not supported at remote Agent \""+
          name+"\"'s address");
      return;
    }

    String host = addr.getHostName();

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
