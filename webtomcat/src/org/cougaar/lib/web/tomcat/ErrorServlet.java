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
package org.cougaar.lib.web.tomcat;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.*;
import javax.servlet.http.*;

/**
 * A simple error servlet for PAGE-NOT-FOUND messages.
 * <p>
 * This allows the ServletEngine to display custom
 * error messages with a 404 error-code.
 */
public class ErrorServlet implements Servlet {

  public void service(
      ServletRequest req,
      ServletResponse res) throws ServletException, IOException {
    HttpServletRequest httpReq;
    HttpServletResponse httpRes;
    try {
      httpReq = (HttpServletRequest) req;
      httpRes = (HttpServletResponse) res;
    } catch (ClassCastException cce) {
      // not an http request?
      throw new ServletException("non-HTTP request or response");
    }

    httpRes.setContentType("text/html");

    String msg = 
      (String) req.getAttribute("javax.servlet.error.message");
    if (msg == null) {
      msg = "Not Found";
    }
    if (!(msg.regionMatches(true, 0, "<html>", 0, 6))) {
      msg = 
        "<html><head><title>"+msg+" (404)</title></head>\n"+
        "<body><h1>"+msg+" (404)</h1>\n"+
        "<p>"+
        "<b>Not found request:</b> "+httpReq.getRequestURI()+
        "</body></html>\n";
    }

    PrintWriter out = httpRes.getWriter();
    out.print(msg);
  }

  private ServletConfig config;
  public void init(ServletConfig config) {
    this.config = config;
  }
  public ServletConfig getServletConfig() {
    return config;
  }
  public String getServletInfo() {
    return "error";
  }
  public void destroy() {
    // ignore
  }

}
