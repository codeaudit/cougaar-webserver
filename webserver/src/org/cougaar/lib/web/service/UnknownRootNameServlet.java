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
package org.cougaar.lib.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.lib.web.arch.ServletRegistry;

/**
 * A servlet that displays an error message when an unknown
 * name ("/$[~]name") is requested.
 * <p>
 * This is somewhat similar to the {@link ListRegistryServlet}.
 */
public class UnknownRootNameServlet 
implements Servlet {

  // read-only registry
  ServletRegistry reg;

  public UnknownRootNameServlet(
      ServletRegistry reg) {
    this.reg = reg;
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

    displayErrorPage(httpReq, httpRes);
  }

  /**
   * Utility method.
   */
  public final void displayErrorPage(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // generate an HTML error response, with a 404 error code.
    //
    // use "setStatus" instead of "sendError" -- see bug 1259

    res.setContentType("text/html");
    res.setStatus(HttpServletResponse.SC_NOT_FOUND);
    res.addHeader("Cougaar-error", "agent");
    PrintWriter out = res.getWriter();

    // get the "/$[~]name[/.*]"
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

    List localNames = reg.listNames();
    int n = ((localNames != null) ? localNames.size() : 0);

    String queryString = req.getQueryString();
    queryString = (queryString == null ? "" : "?"+queryString);

    // put the name at the front of the title, in case this page is
    // displayed in a tiny frame.
    String title = "\""+ name+"\" Not Found"; 

    out.print(
        "<html><head><title>"+
        title+
        "</title></head>\n"+
        "<body><p><h1>"+
        title+
        "</h1>\n"+
        "<p>Local agents:<ol>\n");
    for (int i = 0; i < n; i++) {
      String ni = (String) localNames.get(i);
      String li = "/$"+ni+trimPath+queryString;
      out.print("<li><a href=\""+li+"\">"+li+"</a></li>\n");
    }
    out.print("</ol></body></html>");

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
