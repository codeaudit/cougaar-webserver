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
package org.cougaar.lib.web.tomcat;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A simple error servlet for PAGE-NOT-FOUND messages.
 * <p>
 * This allows the ServletEngine to display custom
 * error messages with a 404 error-code.
 * <p>
 * <b>NOTE:</b> Tomcat 4+ encodes the error message, which prevents 
 * the servlet from embedding HTML in the error response.  For 
 * example, all '&lt;' characters are converted into "&amp;lt;"
 * strings.  Servlets that need to generate an HTML response should
 * use (the discouraged) "setStatus(..)" instead of "sendError(..)"
 * response method.  See bug 1259 for details.
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

    // Integer statusObj = (Integer)
    //   req.getAttribute("javax.servlet.error.status_code");
    // int statusInt = 
    //   ((statusObj != null) ? statusObj.getValue() : -1);
    // assert (HttpServletResponse.SC_NOT_FOUND == statusInt);

    httpRes.setStatus(HttpServletResponse.SC_NOT_FOUND);

    String msg = 
      (String) req.getAttribute("javax.servlet.error.message");
    
    PrintWriter out = httpRes.getWriter();

    out.print(
      "<html><head><title>"+msg+" (404)</title></head>\n"+
      "<body><h1>"+msg+" (404)</h1>\n"+
      "<p>"+
      "<b>Not found request:</b> "+httpReq.getRequestURI()+
      "</body></html>\n");
    out.close();
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
