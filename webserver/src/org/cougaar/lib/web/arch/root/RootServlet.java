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
import org.cougaar.lib.web.arch.util.PrefixMatch;

/**
 * This is a <code>Servlet</code> that uses a 
 * <code>ServletRegistry</code> to delegate Servlet requests.
 * <p>
 * There are four request cases:
 * <ol>
 *   <li>If the request-path is "/" then the
 *       <code>welcomeServlet</code> is used.</li>
 *   <li>If the name in "/$[~]name[/.*]" is not locally registered
 *       then the <code>redirectServlet</code> is used.</li>
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

  /** Servlet to handle "/" requests. */
  private final Servlet welcomeServlet;

  /** Servlet to handle "/agents" requests. */
  private final Servlet agentsServlet;

  /**
   * Servlet to handle all non-local "/$name[/.*]" requests.
   *
   * @see RootRedirectServlet
   */
  private final Servlet redirectServlet;

  public RootServlet(
      ServletRegistry servletReg,
      String rootName,
      Servlet welcomeServlet,
      Servlet agentsServlet,
      Servlet redirectServlet) {
    this.servletReg = servletReg;
    this.rootName = rootName;
    this.welcomeServlet = welcomeServlet;
    this.agentsServlet = agentsServlet;
    this.redirectServlet = redirectServlet;

    // null-check
    String s =
     (servletReg == null ? "servletReg" :
      rootName == null ? "rootName" :
      welcomeServlet == null ? "welcomeServlet" :
      redirectServlet == null ? "redirectServlet" :
      null);
    if (s != null) {
      throw new IllegalArgumentException("null "+s);
    }

    // initialize the servlets

    try {
      welcomeServlet.init(getServletConfig());
    } catch (ServletException se) {
      throw new RuntimeException(
          "Unable to initialize the no-name-servlet: "+
          se.getMessage());
    }

    try {
      redirectServlet.init(getServletConfig());
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

    // look for "/"
    int pathLength = (path == null ? 0 : path.length());
    if (pathLength <= 1) {
      // name not specified
      return welcomeServlet;
    }

    // look for "/$[~]name[/*]", default to rootName
    String name = rootName;
    if (pathLength >= 2 && 
        path.charAt(0) == '/' &&
        path.charAt(1) == '$') {
      int j = path.indexOf('/', 2);
      if (j < 0) {
        j = pathLength;
      }
      if (j > 2) {
        char ch = path.charAt(2); 
        // look for "~", which wget encodes as "%7E"
        if (ch == '~' || 
            (ch == '%' &&
             path.regionMatches(3, "7E", 0, 2))) {
          // check to make sure we contain the name
          int i = 2 + (ch == '~' ? 1 : 3); 
          if (i < j &&
              servletReg.get(path.substring(i, j)) == null) {
            return redirectServlet;
          }
          // we contain "~name", use the root name
        } else if (j >= 3) {
          // extract the name
          name = path.substring(2, j);
        }
      }
    } else if (
        path != null &&
        path.startsWith("/agents") &&
        (pathLength == 7 || path.charAt(7) == '/')) {
      // awkward placement of the "/agents" servlet here, since it
      // has root-level knowledge of the globReg.  Ideally it'd run
      // in the node-agent's LeafServlet, but that's loaded outside
      // the root-level service, so it doesn't have access to the
      // globReg (unless we added a service!).
      return agentsServlet;
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
      s = redirectServlet;
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
