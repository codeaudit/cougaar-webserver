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
 * There are five request cases:
 * <ol>
 *   <li>If the request-path does not start with "/$" then the
 *       <code>noNameServlet</code> is used.</li>
 *   <li>If the request is "/$[/.*]" then the
 *       <code>emptyNameServlet</code> is used.</li>
 *   <li>If the name in "/$[~]name[/.*]" is not locally registered
 *       then the <code>unknownNameServlet</code> is used.</li>
 *   <li>If the request is "/$~[name[/.*]]" then the request is
 *       handled as if it was "/$<i>rootName</i>[/.*]"</li>
 *   <li>If the name is locally registered then the request is
 *       delegated to the registered Servlet.</li>
 * </ol>
 * <p>
 * The most common case is "/$name/<i>path</i>", such as "/$X/test",
 * which would invoke X's "/test" servlet.
 * <p>
 * The "/$~" prefix is used to access the local "root" for a name.
 * For example, if the rootName is "R" and the path is "/$~X/test",
 * then this would invoke R's "/test" servlet.  An equivalent path
 * is "/$~/test".
 */
public class RootServlet 
implements Servlet {

  /** Global (name, Servlet) registry. */
  private final ServletRegistry servletReg;

  /** Name for "/$~" requests. */
  private final String rootName;

  /** Servlet to handle all non-"/$*" requests. */
  private final Servlet noNameServlet;

  /** Servlet to handle all "/$[/.*]" requests. */
  private final Servlet emptyNameServlet;

  /**
   * Servlet to handle all non-local "/$name[/.*]" requests.
   *
   * @see RoootRedirectServlet
   */
  private final Servlet unknownNameServlet;

  public RootServlet(
      ServletRegistry servletReg,
      String rootName,
      Servlet noNameServlet,
      Servlet emptyNameServlet,
      Servlet unknownNameServlet) {
    this.servletReg = servletReg;
    this.rootName = rootName;
    this.noNameServlet = noNameServlet;
    this.emptyNameServlet = emptyNameServlet;
    this.unknownNameServlet = unknownNameServlet;

    // null-check
    String s =
     (servletReg == null ? "servletReg" :
      rootName == null ? "rootName" :
      noNameServlet == null ? "noNameServlet" :
      emptyNameServlet == null ? "emptyNameServlet" :
      unknownNameServlet == null ? "unknownNameServlet" :
      null);
    if (s != null) {
      throw new IllegalArgumentException("null "+s);
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

    // look for "/$[~]name[/*]"
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

    // look for "/$[~]name[/*]"
    char ch = name.charAt(0); 
    int j;
    if (ch == '~') {
      j = 1;
    } else if (
        (ch == '%') &&
         name.regionMatches(1, "7E", 0, 2)) {
      // wget encodes "~" as "%7E"
      j = 3;
    } else {
      j = 0;
    }
    if (j > 0) {
      // check to make sure we contain the name
      name = name.substring(j);
      if (name.length() > 0 &&
          servletReg.get(name) == null) {
        return unknownNameServlet;
      }
      // use the root name
      name = rootName;
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
