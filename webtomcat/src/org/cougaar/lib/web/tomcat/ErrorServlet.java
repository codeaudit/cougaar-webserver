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
