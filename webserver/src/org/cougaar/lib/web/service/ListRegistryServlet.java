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
import java.util.Collections;
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
 * A servlet that displays all the paths in a 
 * <code>ServletRegistry</code>, for <u>leaf</u> servlet
 * service use (within an Agent).
 * <p>
 * <ul>
 *   <li>"?format=html" -- generate a pretty html 
 *       page (default)</li>
 *   <li>"?format=text  -- generate plain text, one path
 *       name per line (instead of "?format=html")</li>
 *   <li>"?sorted=true  -- sort the paths in alphabetical 
 *       order (default)</li>
 *   <li>"?sorted=false" -- don't sort the paths into
 *       alphabetical order (instead of "?sorted=true")</li>
 * </ul>
 */
public class ListRegistryServlet 
implements Servlet {

  // read-only registry
  private final ServletRegistry reg;
  private final String realName;

  public ListRegistryServlet(
      ServletRegistry reg,
      String realName) {
    this.reg = reg;
    this.realName = realName;
    //
    String s = 
      (reg == null ? "reg" :
       realName == null ? "realName" :
       null);
    if (s != null) {
      throw new IllegalArgumentException("null "+s);
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

    displayList(httpReq, httpRes);
  }

  /**
   * Utility method.
   */
  public final void displayList(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // scan url-parameters for:
    //   "?format=[text|html]"
    //   "?sorted=[true|false]"
    boolean useHtml = !("text".equals(req.getParameter("format")));
    boolean sorted = !("false".equals(req.getParameter("sorted")));

    // get the "/$name[/.*]"
    String path = req.getRequestURI();
    // assert ((path != null) && (path.startsWith("/$")))
    int sepIdx = path.indexOf('/', 2);
    if (sepIdx < 0) {
      sepIdx = path.length();
    }
    String name = path.substring(2, sepIdx);

    List pathList = reg.listNames();
    int n = ((pathList != null) ? pathList.size() : 0);

    if (sorted && (n > 0)) {
      Collections.sort(pathList);
    }

    res.setContentType(
        (useHtml ? "text/html" : "text/plain"));

    PrintWriter out = res.getWriter();

    if (useHtml) {
      String title =
        "List of \""+
        name+
        "\""+
        (name.equals(realName) ? "" : (" ("+realName+")"))+
        " Servlets";
      out.print(
          "<html><head><title>"+
          title+
          "</title></head>\n"+
          "<body><p><h1>"+
          title+
          "</h1>\n"+
          "<p><ol>\n");
      for (int i = 0; i < n; i++) {
        String pi = (String) pathList.get(i);
        out.print(
            "<li><a href=\"/$"+
            name+pi+
            "\">/$"+
            name+pi+
            "</a></li>\n");
      }
      out.print("</ol></body></html>\n");
    } else {
      for (int i = 0; i < n; i++) {
        String pi = (String) pathList.get(i);
        out.println("/$"+name+pi);
      }
    }
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
    return "list-registry";
  }
  public void destroy() {
    // ignore
  }

}
