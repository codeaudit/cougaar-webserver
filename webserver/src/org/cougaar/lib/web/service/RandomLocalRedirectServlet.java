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

import org.cougaar.lib.web.arch.ServletRegistry;

/**
 * A servlet that redirects an empty-name request ("/$" or "/$[/.*]")
 * and missing-name request (no "/$name") to a <b>random</b> local 
 * name.
 * <p>
 * The "/$/" is useful when one can assume that any locally-registered
 * Servlet can handle the request.  This is a new supported usage.
 * <p>
 * I'd like to remove the missing-name request and force users to 
 * <i>always</i> specify a name, but for now this is necessary for 
 * backwards compatibility.  Consider it <i>deprecated</i>...
 *
 * @see UnknownRootNameServlet
 */
public class RandomLocalRedirectServlet 
implements Servlet {

  private Random rand;
  private ServletRegistry reg;

  public RandomLocalRedirectServlet(
      ServletRegistry reg) {
    this.reg = reg;

    // null-check
    if (reg == null) {
      throw new NullPointerException();
    }

    this.rand = new Random();
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
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // get the listing of all local names
    List localNames = reg.listNames();
    int n = ((localNames != null) ? localNames.size() : 0);
    if (n <= 0) {
      res.sendError(
          HttpServletResponse.SC_NOT_FOUND, 
          "Zero local agents");
      return;
    }

    // select the random name
    int randIdx = rand.nextInt(n);
    String randName = (String) localNames.get(randIdx);

    // get the "/$[/.*]"
    String path = req.getRequestURI();
    String trimPath;
    if (path.startsWith("/$")) {
      trimPath = path.substring(2);
    } else {
      trimPath = path;
    }

    String queryString = 
      req.getQueryString();

    // create the new location string
    String location = 
      "/$"+
      randName+
      trimPath+
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

  private ServletConfig config;
  public void init(ServletConfig config) {
    this.config = config;
  }
  public ServletConfig getServletConfig() {
    return config;
  }
  public String getServletInfo() {
    return "random-local-redirect";
  }
  public void destroy() {
    // ignore
  }

}
