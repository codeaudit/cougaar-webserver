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
package org.cougaar.lib.web.arch.leaf;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.util.DummyServletConfig;
import org.cougaar.lib.web.arch.util.PrefixMatch;

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

  public void service(
      ServletRequest req,
      ServletResponse res) throws ServletException, IOException {
    HttpServletRequest hreq;
    try {
      hreq = (HttpServletRequest) req;
    } catch (ClassCastException cce) {
      // not an HTTP request?
      throw new ServletException("non-HTTP request or response");
    }

    // get the path
    String path = hreq.getRequestURI();

    // look for "/$[~]name[/innerPath]"
    int i = path.indexOf('/', 2);
    if (i < 0) {
      i = path.length();
    }
    String innerPath = path.substring(i);

    // find the matching servlet
    Object o = servletReg.get(innerPath);
    Servlet servlet;
    String pathInfo = null;
    String servletPath = path;
    if (o == null) {
      servlet = unknownPathServlet; // no such path
    } else if (o instanceof Servlet) {
      servlet = (Servlet) o;
    } else {
      PrefixMatch pm = (PrefixMatch) o;
      servlet = (Servlet) pm.getValue();
      servletPath = pm.getPrefix();
      pathInfo = pm.getTail();
    }

    // override the request
    ServletRequest sr =
      new MyRequestWrapper(
          hreq, 
          pathInfo, 
          servletPath);

    // invoke the servlet
    servlet.service(sr, res);
  }

  public void destroy() {
    // ignore -- we're using the dummy config...
  }

  private static final class MyRequestWrapper
    extends HttpServletRequestWrapper {
      private final String pathInfo;
      private final String servletPath;
      public MyRequestWrapper(
          HttpServletRequest req,
          String pathInfo,
          String servletPath) {
        super(req);
        this.pathInfo = pathInfo;
        this.servletPath = servletPath;
      }
      public String getPathInfo() {
        return pathInfo;
      }
      public String getServletPath() {
        return servletPath;
      }
    }
}
