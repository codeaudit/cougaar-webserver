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
      unknownPathServlet.init(getServletConfig());
    } catch (ServletException se) {
      throw new RuntimeException(
          "Unable to initialize the unknown-path-servlet: "+
          se.getMessage());
    }
  }

  public void init(ServletConfig config) throws ServletException {
    servletReg.init(config);
  }

  public ServletConfig getServletConfig() {
    return servletReg.getServletConfig();
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

    // look for "[/$[~]name][/innerPath]"
    String innerPath = path;
    int pathLength = (path == null ? 0 : path.length());
    if (pathLength >= 2 && 
        path.charAt(0) == '/' &&
        path.charAt(1) == '$') {
      int i = path.indexOf('/', 2);
      if (i < 0) {
        i = pathLength;
      }
      innerPath = path.substring(i);
    }

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
