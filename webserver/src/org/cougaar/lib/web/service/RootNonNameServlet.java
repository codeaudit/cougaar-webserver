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
 *       Generates a help page.</li>
 *   <li>"/help"<br>
 *       Generates a help page.</li>
 *   <li>"/$[/.*]"<br>
 *       Redirects the request to a <b>RANDOM</b> local 
 *       agent name.  This is userful when the client
 *       can assume that any locally-registered agent
 *       can handle the request.</li>
 *   <li>"/agents[?args]"<br>
 *       Lists agent names.  The supported URL-parameter 
 *       arguments are:<ul>
 *         <li>"?local" -- local list of agent names 
 *             (default)</li>
 *         <li>"?all"   -- global list of all agent 
 *             names (instead of "?local")</li>
 *         <li>"?html"  -- generate a pretty html 
 *             page (default)</li>
 *         <li>"?text   -- generate plain text, one agent
 *             name per line (instead of "?html")</li>
 *       </ul>
 *       Note this nice feature: With the normal "/$name" 
 *       redirects and this "/agents" support, the client 
 *       can request "/$name/agents" to list all 
 *       co-located agents, regardless of where the named 
 *       agent happens to be located.</li>
 *   <li>"/<i>other</i>"<br>
 *       Same as "/$/<i>other</i>", assuming that 
 *       "<i>other</i>" != "agents" and 
 *       "<i>other</i>" != "/$<i>text</i>/.*"</li>.
 *       I'd like to remove this case and force users to 
 *       <i>always</i> specify a name, but for now this is 
 *       necessary for backwards compatibility.  Consider 
 *       it <i>deprecated</i>...
 * </ul>
 * <p>
 */
public class RootNonNameServlet 
implements Servlet {

  private static final Random rand = new Random();

  // read-only registries:
  private ServletRegistry localReg;
  private GlobalRegistry globReg;

  public RootNonNameServlet(
      ServletRegistry localReg,
      GlobalRegistry globReg) {
    this.localReg = localReg;
    this.globReg = globReg;

    // null-check
    if ((localReg == null) ||
        (globReg == null)) {
      throw new NullPointerException();
    }
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
      listAgents(req, res);
      return;
    } else if ((path.length() <= 2) ||
               (path.equals("/help"))) {
      displayHelp(req, res);
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
    String msg =
      "<html><head><title>"+
      "Welcome to Cougaar"+
      "</title></head><body>\n"+
      "<h2>Welcome to "+
      "<a href=\"http://www.cougaar.org\">Cougaar</a></h2>\n"+
      "Options:<ul>\n"+
      "<li><a href=\"/agents\">List local agents</a></li>\n"+
      "<li><a href=\"/agents?all\">List all agents</a></li>\n"+
      "</ul>\n"+
      "</body></html>";
    res.sendError(
      HttpServletResponse.SC_NOT_FOUND, 
      msg);
  }

  private final void listAgents(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // local v.s. global listing
    boolean listLocals = true;
    // html v.s. plain-text response
    boolean useHtml = true;

    // scan url-parameters for:
    //  "?all", "?text", "?local", "?html"
    for (Enumeration en = req.getParameterNames();
        en.hasMoreElements();
        ) {
      String pName = (String) en.nextElement();
      if ("all".equals(pName)) {
        listLocals = false;
      } else if ("text".equals(pName)) {
        useHtml = false;
      } else if ("local".equals(pName)) {
        listLocals = true;
      } else if ("html".equals(pName)) {
        useHtml = true;
      }
    }

    res.setContentType(
        (useHtml ? "text/html" : "text/plain"));

    PrintWriter out = res.getWriter();

    // get the listing
    List names =
      (listLocals ?
       localReg.listNames() :
       globReg.listNames());
    int n = ((names != null) ? names.size() : 0);

    if (!(useHtml)) {
      // simple line-by-line output
      for (int i = 0; i < n; i++) {
        String ni = (String) names.get(i);
        out.println(ni);
      }
      out.close();
    } else {
      // pretty HTML
      String title =
        (listLocals ?
         ("Local \""+
          req.getServerName()+
          ":"+
          req.getServerPort()+
          "\" Agents List") :
         ("All Agents List"));
      out.print("<html><head><title>");
      out.print(title);
      out.print(
          "</title></head>\n"+
          "<body><p><h1>");
      out.print(title);
      out.print("</h1>\n");
      if (n > 0) {
        out.print("<ol>\n");
        for (int i = 0; i < n; i++) {
          String ni = (String) names.get(i);
          out.print("<li><a href=\"/$");
          out.print(ni);
          out.print("/list\">");
          out.print(ni);
          out.print("</a></li>\n");
        }
        out.print("</ol>\n");
      } else {
        out.print("<font color=\"red\">zero agents</font>");
      }
      if (listLocals) {
        out.print(
            "<p>\n"+
            "<h2>"+
            "<a href=\"/agents?all\">List all agents</a>"+
            "</h2>\n");
      }
      out.print("</body></html>");
      out.close();
    }
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
