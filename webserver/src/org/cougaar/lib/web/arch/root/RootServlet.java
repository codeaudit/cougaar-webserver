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
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.util.DummyServletConfig;
import org.cougaar.lib.web.arch.util.PrefixMatch;

/**
 * This is a <code>Servlet</code> that uses a 
 * <code>ServletRegistry</code> to delegate Servlet requests.
 * <p>
 * There are four request cases:
 * <ol>
 *   <li>If the request-path does not start with "/$" then the
 *       "noNameServlet" is used.</li>
 *   <li>If the request is "/$[/.*]" then the "emptyNameServlet" 
 *       is used.</li>
 *   <li>If the "/$name[/.*]" is not known then the 
 *       "unknownNameServlet" is used.</li>
 *   <li>If the name has been registered then the request is 
 *       delegated to the registered Servlet.</li>
 * </ol>
 */
public class RootServlet 
implements Servlet {

  /**
   * Global (name, Servlet) registry.
   */
  private final ServletRegistry servletReg;

  /**
   * Servlet to handle all non-"/$*" requests.
   */
  private final Servlet noNameServlet;

  /**
   * Servlet to handle all "/$[/.*]" requests.
   */
  private final Servlet emptyNameServlet;

  /**
   * Servlet to handle all non-local "/$name[/.*]" requests.
   *
   * @see RoootRedirectServlet
   */
  private final Servlet unknownNameServlet;

  public RootServlet(
      ServletRegistry servletReg,
      Servlet noNameServlet,
      Servlet emptyNameServlet,
      Servlet unknownNameServlet) {
    this.servletReg = servletReg;
    this.noNameServlet = noNameServlet;
    this.emptyNameServlet = emptyNameServlet;
    this.unknownNameServlet = unknownNameServlet;

    // null-check
    if (servletReg == null) {
      throw new NullPointerException();
    } else if (noNameServlet == null) {
      throw new NullPointerException();
    } else if (emptyNameServlet == null) {
      throw new NullPointerException();
    } else if (unknownNameServlet == null) {
      throw new NullPointerException();
    }

    // initialize the servlets

    try {
      noNameServlet.init(DummyServletConfig.getInstance());
    } catch (ServletException se) {
      throw new RuntimeException(
          "Unable to initialize the no-name-servlet: "+
          se.getMessage());
    }

    try {
      emptyNameServlet.init(DummyServletConfig.getInstance());
    } catch (ServletException se) {
      throw new RuntimeException(
          "Unable to initialize the empty-name-servlet: "+
          se.getMessage());
    }

    try {
      unknownNameServlet.init(DummyServletConfig.getInstance());
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
    return "root-servlet";
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

    // look for "/$*"
    int pathLength;
    if ((path == null) ||
        ((pathLength = path.length()) < 2) ||
        (path.charAt(1) != '$')) {
      // name not specified
      return noNameServlet;
    }

    // look for "/$name[/*]"
    int i = path.indexOf('/', 2);
    if (i < 0) {
      i = pathLength;
    }
    String name = path.substring(2, i);

    // check for the empty name
    if (name.length() == 0) {
      // empty name
      return emptyNameServlet;
    }

    // find the matching servlet
    Object o = servletReg.get(name);
    Servlet s;
    if (o == null) {
      s = null;
    } else if (o instanceof Servlet) {
      s = (Servlet) o;
    } else {
      // unexpected!
      s = (Servlet) ((PrefixMatch) o).getValue();
    }

    // no such name
    if (s == null) {
      s = unknownNameServlet;
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
