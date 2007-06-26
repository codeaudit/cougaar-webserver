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
import java.util.Random;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.root.GlobalRegistry;

/**
 * A servlet that generates a top-level HTML welcome page in response
 * to the "/" path.
 */
public class WelcomeServlet implements Servlet {

  private final String localNode;

  public WelcomeServlet(String localNode) {
    this.localNode = localNode;
  }

  public void service(
      ServletRequest sreq, ServletResponse sres
      ) throws ServletException, IOException {

    // cast
    HttpServletRequest req = (HttpServletRequest) sreq;
    HttpServletResponse res = (HttpServletResponse) sres;

    // write response
    res.setContentType("text/html");
    PrintWriter out = res.getWriter();
    out.print(
      "<html><head><title>"+
      "Welcome to Cougaar"+
      "</title></head><body>\n"+
      "<h2>Welcome to "+
      "<a href=\"http://www.cougaar.org\">Cougaar</a></h2>\n"+
      "Options:<ul>\n"+
      "<li><a href=\"/agents\"><b>Local</b> agents on node "+localNode+
      "</a></li><p>\n"+
      "<li><a href=\"/agents?suffix=.\"><b>All</b> agents in the society</a>"+
      "</li>\n"+
      "</ul>\n"+
      "</body></html>");
    out.close();
  }

  // etc
  private ServletConfig config;
  public void init(ServletConfig config) { this.config = config; }
  public ServletConfig getServletConfig() { return config; }
  public String getServletInfo() { return "welcome"; }
  public void destroy() { }
}
