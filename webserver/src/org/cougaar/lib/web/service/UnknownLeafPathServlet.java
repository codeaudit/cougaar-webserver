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

import org.cougaar.lib.web.arch.ServletRegistry;

/**
 * A "/$name[/.*]" level servlet that<ol>
 *   <li>generates a help message for "/$name" and "/$name/" 
 *       requests</li>
 *   <li>generates an error message for all other paths</li>
 * </ol>
 */
public class UnknownLeafPathServlet 
implements Servlet {

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

    // get the "/$name[/.*]"
    String path = req.getRequestURI();
    // assert ((path != null) && (path.startsWith("/$")))
    String name;
    int sepIdx = path.indexOf('/', 2);
    if (sepIdx < 0) {
      name = path.substring(2);
    } else {
      name = path.substring(2, sepIdx);
      if (sepIdx < (path.length() - 1)) {
        name = path.substring(2, sepIdx);
        String trimPath = path.substring(sepIdx);
        displayErrorPage(req, res, name, trimPath);
        return;
      }
    }
    displayHelpPage(req, res, name);
  }

  private final void displayHelpPage(
      HttpServletRequest req,
      HttpServletResponse res,
      String name) throws ServletException, IOException {
    res.setContentType("text/html");
    PrintWriter out = res.getWriter();
    out.print(
      "<html><head><title>"+
      "Agent "+
      name+
      "</title></head><body>\n"+
      "<h2>Agent "+
      name+
      "</h2>\n"+
      "Options:<ul>\n"+
      "<li><a href=\"/$"+
      name+
      "/list\">List paths in agent \""+
      name+
      "\"</a></li>\n"+
      "<li><a href=\"/agents\">List agents co-located with agent \""+
      name+
      "\"</a></li>\n"+
      "<li><a href=\"/agents?scope=all\">List all agents</a></li>\n"+
      "</ul>\n"+
      "</body></html>");
    out.close();
  }

  private final void displayErrorPage(
      HttpServletRequest req,
      HttpServletResponse res,
      String name,
      String path) throws ServletException, IOException {

    // generate an HTML error response, with a 404 error code.
    //
    // use "setStatus" instead of "sendError" -- see bug 1259

    res.setContentType("text/html");
    res.setStatus(HttpServletResponse.SC_NOT_FOUND);
    PrintWriter out = res.getWriter();

    String title = 
      "Unknown Servlet Path \""+
      path+
      "\" within Agent \""+
      name+
      "\"";

    out.print(
        "<html><head><title>"+
        title+
        "</title></head>\n"+
        "<body><p><h1>"+
        title+
        "</h1>\n"+
        "<p>Options:<ul>\n"+
        "<li><a href=\"/$"+
        name+
        "/list\">List paths in agent \""+
        name+
        "\"</a></li>\n"+
        "<li><a href=\"/agents\">List agents co-located with agent \""+
        name+
        "</a></li>\n"+
        "<li><a href=\"/agents?scope=all\">List all agents</a></li>\n"+
        "</ul>\n"+
        "</body></html>");
    out.close();
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
    return "unknown-root-name";
  }
  public void destroy() {
    // ignore
  }

}
