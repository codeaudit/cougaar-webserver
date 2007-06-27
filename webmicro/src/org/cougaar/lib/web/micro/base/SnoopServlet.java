/*
 * <copyright>
 *  
 *  Copyright 1997-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.micro.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet is a minimal test servlet for server debugging.
 */
public class SnoopServlet extends HttpServlet {

  public void doGet(HttpServletRequest req, HttpServletResponse res
      ) throws IOException, ServletException {
    if ("/404".equals(req.getRequestURI())) {
      res.sendError(404, "not found");
      PrintWriter out = res.getWriter();
      out.println("not_found_here");
      return;
    }

    res.setContentType("text/html");
    PrintWriter out = res.getWriter();
    out.println(
        "<html><body><h1>HELLO WORLD!</h1><p>");

    out.println("<pre>");
    out.println("req.serverName="+req.getServerName());
    out.println("req.serverPort="+req.getServerPort());
    out.println("req.remoteAddr="+req.getRemoteAddr());
    out.println("req.remoteHost="+req.getRemoteHost());
    out.println("req.secure="+req.isSecure());
    out.println("req.scheme="+req.getScheme());
    out.println("req.protocol="+req.getProtocol());
    out.println("req.contentLength="+req.getContentLength());
    out.println("req.contentType="+req.getContentType());
    for (Enumeration en = req.getParameterNames(); en.hasMoreElements(); ) {
      String s = (String) en.nextElement();
      out.println("req.param "+s+"="+req.getParameter(s));
    }
    out.println("req.method="+req.getMethod());
    out.println("req.pathInfo="+req.getPathInfo());
    out.println("req.servletPath="+req.getServletPath());
    out.println("req.queryString="+req.getQueryString());
    out.println("req.requestURI="+req.getRequestURI());
    out.println("req.requestURL="+req.getRequestURL());
    for (Enumeration en = req.getHeaderNames(); en.hasMoreElements(); ) {
      String s = (String) en.nextElement();
      out.println("req.header "+s+"="+req.getHeader(s));
    }
    BufferedReader br = req.getReader();
    while (true) {
      String s = br.readLine();
      if (s == null) break;
      out.println("req.body "+s);
    }
    out.println("</pre>");

    out.print("<form method=\"GET\" action=\"");
    out.print(req.getRequestURI());
    out.println("\">");
    out.println("Foo: <select name=\"foo\">");
    out.println("  <option>Alpha</option>");
    out.println("  <option>Beta</option>");
    out.println("</select>");
    out.println(
        "<input type=\"submit\" value=\"Submit\">\n"+
        "</form>");

    out.println("</body></html>");
  }
}
