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
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.util.PathParser;
import org.cougaar.lib.web.arch.util.PrefixMatch;

/**
 * This <code>Servlet</code> handles all incoming requests,
 * typically by delegating "/$name" requests to the local {@link
 * org.cougaar.lib.web.arch.leaf.LeafServlet}s or by redirecting
 * the client if the named agent is remote.
 * <p>
 * The following rules are used to handle requests:
 * <ol>
 *   <li>If the request path is "/" then the
 *       <code>welcomeServlet</code> is used to print a friendly
 *       welcome page.</li>
 *   <li>If the path is "/agent" then the <code>agentsServlet</code>
 *       is used.</li>
 *   <li>If the name lacks a "/$" prefix, or is "/$/", or is "/$~/",
 *       then the request is handled as if the prefix is
 *       "/$<i>rootName</i>", and the following rules are invoked.
 *       </li>
 *   <li>If the "/$name[/.*]" name is locally registered then the
 *       request is delegated to the registered LeafServlet.  The
 *       LeafServlet in turn invokes the registered path within that
 *       name, or complains if the path does not exist.  This is the
 *       most common case.</li>
 *   <li>If the "/$name[/.*]" name is not locally registered then the
 *       <code>Redirector</code> is used to redirect the client
 *       to the appropriate remote host, based on a nameserver
 *       lookup.</li>
 *   <li>If the prefix is "/$~name[/.*]" and the name is locally
 *       registered then the request is passed to the
 *       "/$<i>rootName</i>" -- the "~" is interpreted as
 *       "the home of <i>name</i>".</li>
 *   <li>If the prefix is "/$~name[/.*]" and the name is not locally
 *       registered then the <code>Redirector</code> is used as
 *       noted above.<li>
 * </ol>
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

  /** Handler for all non-local "/$name[/.*]" requests. */
  private final Redirector redirector;

  public RootServlet(
      ServletRegistry servletReg,
      String rootName,
      Servlet welcomeServlet,
      Servlet agentsServlet,
      Redirector redirector) {
    this.servletReg = servletReg;
    this.rootName = rootName;
    this.welcomeServlet = welcomeServlet;
    this.agentsServlet = agentsServlet;
    this.redirector = redirector;

    // null-check
    String s =
     (servletReg == null ? "servletReg" :
      rootName == null ? "rootName" :
      welcomeServlet == null ? "welcomeServlet" :
      redirector == null ? "redirector" :
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
  }

  public void service(
      ServletRequest sreq, ServletResponse sres
      ) throws ServletException, IOException {

    // cast
    HttpServletRequest req = (HttpServletRequest) sreq;
    HttpServletResponse res = (HttpServletResponse) sres;

    // trim off basePath, e.g. "/foo/$bar" -> "/$bar"
    String path = req.getRequestURI();
    int pathLength = (path == null ? 0 : path.length());
    String contextPath = req.getContextPath();
    int contextLength = (contextPath == null ? 0 : contextPath.length());
    if (contextLength > 0 && contextLength <= pathLength) {
      path = path.substring(contextLength);
    }

    // look for "/"
    if (pathLength <= 1) {
      welcomeServlet.service(sreq, sres);
      return;
    }

    // parse path
    PathParser pathInfo = new PathParser(path);
    String name = pathInfo.getName();
    boolean is_node_of = pathInfo.isNodeOf();
    List options = pathInfo.getOptions();
    String subpath = pathInfo.getSubpath();

    if (name == null || name.length() == 0) {
      name = rootName;
    }

    // look for "[/$*]/agents[/*]" where the agent is local
    //
    // this is awkward but currently required, since only the root-level
    // servlet has access to the globReg.  Ideally this servlet would run
    // in the lower-level node's LeafServlet, but that level doesn't have
    // access to our globReg.
    //
    // we also want to catch any local agents, to avoid an unnecessary
    // redirect.  E.g. if "x" is local and the request is "/$x/agents" then
    // we want to handle that here instead of making "/$x/" redirect the
    // client to "/$rootName/agents".
    if (subpath != null &&
        subpath.startsWith("/agents") &&
        (subpath.length() == 7 || subpath.charAt(7) == '/')) {
      boolean is_local = (servletReg.get(name) != null);
      if (is_local) {
        agentsServlet.service(sreq, sres);
        return;
      }
    }

    // check for "is_node_of" flag and local agent
    if (is_node_of) {
      boolean is_local = (servletReg.get(name) != null);
      if (is_local) {
        // act as if the request is for "/$rootName"
        name = rootName;
      }
    }

    // lookup possibly local agent
    Servlet localServlet;
    Object o = servletReg.get(name);
    if (o == null) {
      localServlet = null;
    } else if (o instanceof Servlet) {
      localServlet = (Servlet) o;
    } else {
      // unexpected!
      localServlet = (Servlet) ((PrefixMatch) o).getValue();
    }
    if (localServlet != null) {
      localServlet.service(sreq, sres);
      return;
    }

    // redirect to remote agent
    redirector.redirect(name, options, req, res);
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
  public void destroy() {
    // ignore -- we're using the dummy config...
  }
}
