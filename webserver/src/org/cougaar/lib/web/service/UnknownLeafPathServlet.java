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
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.lib.web.arch.ServletRegistry;

/**
 * A servlet that displays an error message when an unknown
 * leaf path ("/$name/junk") is requested.
 * <p>
 * This can be used as a general "/help" page.  It simply
 * tells the client this agent's name and refers the client 
 * to the "/list" page.
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

    displayErrorPage(httpReq, httpRes);
  }

  /**
   * Utility method.
   */
  public final void displayErrorPage(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // get the "/$name[/.*]"
    String path = req.getRequestURI();
    String name;
    String trimPath;
    if (path.startsWith("/$")) {
      int sepIdx = path.indexOf('/', 2);
      if (sepIdx < 0) {
        name = path.substring(2);
        trimPath = "/";
      } else {
        name = path.substring(2, sepIdx);
        trimPath = path.substring(sepIdx);
      }
    } else {
      // might want to reuse this servlet for missing-name reqs
      name = "";
      trimPath = path;
    }

    String title = 
      "Unknown Servlet Path \""+
      trimPath+
      "\" within Agent \""+
      name+
      "\"";

    StringBuffer buf = new StringBuffer();
    buf.append("<html><head><title>");
    buf.append(title);
    buf.append(
        "</title></head>\n"+
        "<body><p><h1>");
    buf.append(title);
    buf.append(
        "</h1>\n"+
        "<p>Options:<ul>\n"+
        "<li><a href=\"/$");
    buf.append(name);
    buf.append(
        "/list\">List paths in agent \"");
    buf.append(name);
    buf.append(
        "\"</a></li>\n"+
        "<li><a href=\"/agents\">List local agents</a></li>\n"+
        "<li><a href=\"/agents?all\">List all agents</a></li>\n"+
        "</ul>\n"+
        "</body></html>");

    res.sendError(
        HttpServletResponse.SC_NOT_FOUND, 
        buf.toString());
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
