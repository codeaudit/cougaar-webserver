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
package org.cougaar.lib.web.arch.leaf;

import java.io.IOException;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.DummyServletConfig;

/**
 * This is a <code>Servlet</code> that uses a 
 * <code>ServletRegistry</code> to delegate Servlet requests.
 * <p>
 * If the request-path is not registered then the provided
 * "unknownPathServlet" is used.
 */
public class LeafServlet 
implements Servlet {

  /**
   * Local (path, Servlet) registry.
   */
  private final ServletRegistry servletReg;

  /**
   * Servlet to handle unknown paths.
   *
   * @see ErrorServlet
   */
  private final Servlet unknownPathServlet;

  public LeafServlet(
      ServletRegistry servletReg,
      Servlet unknownPathServlet) {
    this.servletReg = servletReg;
    this.unknownPathServlet = unknownPathServlet;

    // null-check
    if (servletReg == null) {
      throw new NullPointerException();
    } else if (unknownPathServlet == null) {
      throw new NullPointerException();
    }

    try {
      unknownPathServlet.init(DummyServletConfig.getInstance());
    } catch (ServletException se) {
      throw new RuntimeException(
          "Unable to initialize the unknown-path-servlet: "+
          se.getMessage());
    }
  }

  public void init(ServletConfig config) throws ServletException {
    // ignore -- we're using the dummy config...
  }

  public ServletConfig getServletConfig() {
    return DummyServletConfig.getInstance();
  }

  public String getServletInfo() {
    return "leaf-servlet";
  }

  private Servlet findServlet(
      ServletRequest req) throws ServletException, IOException {
    // get the path
    String path;
    try {
      path = ((HttpServletRequest)req).getRequestURI();
    } catch (ClassCastException cce) {
      // not an HTTP request?
      throw new ServletException("non-HTTP request or response");
    }

    // find the matching servlet
    Servlet s = servletReg.get(path);

    // no such path
    if (s == null) {
      s = unknownPathServlet;
    }

    return s;
  }

  public void service(
      ServletRequest req,
      ServletResponse res) throws ServletException, IOException {
    findServlet(req).service(req, res);
  }

  public void destroy() {
    // ignore -- we're using the dummy config...
  }

}
