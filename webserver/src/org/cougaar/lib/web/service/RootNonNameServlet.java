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
package org.cougaar.lib.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.lib.web.arch.root.GlobalRegistry;
import org.cougaar.lib.web.arch.ServletRegistry;

/**
 * A servlet that handles all non-"/$name" requests.
 * <p>
 * Specifically, this servlet handles:<ul>
 *   <li>"[/]"<br>
 *       Generates an html help page.</li><p>
 *   <li>"/$[/.*]"<br>
 *       Redirects the request to a <b>RANDOM</b> local agent name.
 *       This is useful when the client can assume that any
 *       locally-registered agent can handle the request.</li><p>
 *   <li>"/robots.txt"<br>
 *       Generates this hard-coded response:<pre>
 *           User-agent: *
 *           Disallow: /
 *       </pre>This keeps out web-crawlers, such as "google".</li><p>
 *   <li>"/agents[?args]"<br>
 *       Invokes the AgentsServlet, which can be used to list
 *       agents on this node and traverse the white pages
 *       listings</li><p>
 *   <li>"/<i>other</i>"<br>
 *       Same as "/$/<i>other</i>", assuming that "/<i>other</i>"
 *       doesn't match one of the cases listed above.
 *       <p>
 *       I'd like to remove this case and force users to <i>always</i>
 *       specify "/$/[.*]", but for now this is necessary for
 *       backwards compatibility.  Consider it <i>deprecated</i>...
 * </ul>
 *
 * @see AgentsServlet handles "/agents" requests
 */
public class RootNonNameServlet 
implements Servlet {

  private static final Random rand = new Random();

  // read-only registry:
  private final ServletRegistry localReg;

  private final AgentsServlet agentsServlet;

  public RootNonNameServlet(
      ServletRegistry localReg,
      GlobalRegistry globReg) {
    this.localReg = localReg;

    // null-check
    if ((localReg == null) ||
        (globReg == null)) {
      throw new NullPointerException();
    }

    agentsServlet = new AgentsServlet(localReg, globReg);
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

    handle(httpReq, httpRes);
  }

  private final void handle(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    String path = req.getRequestURI();
    // assert ((path != null) && (path.startsWith("/")))
    String trimPath;
    if (path.startsWith("/$")) {
      trimPath = path.substring(2);
    } else if (path.equals("/agents")) {
      agentsServlet.service(req, res);
      return;
    } else if (path.length() <= 2) {
      displayHelp(req, res);
      return;
    } else if (path.equals("/robots.txt")) {
      excludeRobots(req, res);
      return;
    } else {
      trimPath = path;
    }
    // assert (trimPath.startsWith("/"))
    randomLocalRedirect(req, res, trimPath);
  }

  private final void displayHelp(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {
    res.setContentType("text/html");
    PrintWriter out = res.getWriter();
    out.print(
      "<html><head><title>"+
      "Welcome to Cougaar"+
      "</title></head><body>\n"+
      "<h2>Welcome to "+
      "<a href=\"http://www.cougaar.org\">Cougaar</a></h2>\n"+
      "Options:<ul>\n"+
      "<li><a href=\"/agents\">Agents on host ("+
      req.getServerName()+":"+req.getServerPort()+
      ")</a></li>\n"+
      "<li><a href=\"/agents?suffix=.\">Agents at the root (.)</a>"+
      "</li>\n"+
      "</ul>\n"+
      "</body></html>");
    out.close();
  }

  private static final void excludeRobots(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {
    // we don't want web-crawlers!
    res.setContentType("text/plain");
    PrintWriter out = res.getWriter();
    out.print("User-agent: *\nDisallow: /\n");
    out.close();
  }

  private final void randomLocalRedirect(
      HttpServletRequest req,
      HttpServletResponse res,
      String trimPath) throws ServletException, IOException {

    // get the listing of all local names
    List localNames = localReg.listNames();
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
