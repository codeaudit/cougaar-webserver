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
package org.cougaar.lib.web.service;

import java.io.IOException;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

/**
 * A servlet that redirects to another port on the same host.
 * <p>
 * This is used as a work-around to redirect unknown Servlet 
 * requests to the old PSP server.
 */
public class OldServerRedirectServlet 
implements Servlet {

  private final int oldPort;

  public OldServerRedirectServlet(
      int oldPort) {
    this.oldPort = oldPort;
  }

  public void service(
      ServletRequest req,
      ServletResponse res) throws ServletException, IOException {

    HttpServletRequest httpReq;
    HttpServletResponse httpRes;
    try {
      httpReq = (HttpServletRequest) req;
      httpRes = (HttpServletResponse) res;
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
      HttpServletRequest httpReq,
      HttpServletResponse httpRes) throws ServletException, IOException {

    String queryString = 
      httpReq.getQueryString();

    // create the new location string
    String location = 
      "http://"+
      "localhost:"+
      oldPort+
      httpReq.getRequestURI()+
      ((queryString != null) ? 
       ("?"+queryString) :
       (""));

    // encode for redirect -- typically a no-op
    location = httpRes.encodeRedirectURL(location);

    // redirect the request to the remote location
    httpRes.sendRedirect(location);
  }

  //
  // other Servlet methods
  //

  private ServletConfig config;
  public void init(ServletConfig config) {
    this.config = config;
  }
  public ServletConfig getServletConfig() {
    return config;
  }
  public String getServletInfo() {
    return "old-server-redirect";
  }
  public void destroy() {
    // ignore
  }

}
