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

import java.io.*;
import java.text.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Servlet that is loaded by both <code>TomcatServletEngine</code>
 * and Tomcat itself -- this is used to delegate requests within
 * Tomcat back into the (launching) TomcatServletEngine's
 * "gateway" Servlet.
 * <p>
 * This kluge depends upon a static variable, which means that
 * only one TomcatServletEngine can run at a time.  In talking
 * to "tomcat-user@jakarta.apache.org" it sounds like this is
 * the easiest way to embed Tomcat.  It's not pretty, but okay
 * for now.
 */ 
public final class HookServlet implements Servlet {
  
  private static Servlet NO_HOOK_SERVLET = new NoHookServlet();
  private static Servlet servlet = NO_HOOK_SERVLET;
  private static ServletConfig config;
  
  public HookServlet() {
  }

  public void init(ServletConfig newConfig) throws ServletException {
    if (config != null) {
      System.err.println("Init when config is non-null!");
    } else {
      config = newConfig; 
      servlet.init(config);
    }
  }

  public String getServletInfo() {
    return "A Hook to the Gateway Servlet";
  }
  
  public ServletConfig getServletConfig() {
    return config;
  }
  
  public void destroy() {
    if (config == null) {
      System.err.println("Destroy when config is null!");
    } else {
      servlet.destroy();
      servlet = NO_HOOK_SERVLET;
      config = null;
    }
  }
  
  public void service (ServletRequest req,
  		       ServletResponse res) throws ServletException, IOException  {
    servlet.service(req, res);
  }
  
  public static void setServlet(Servlet newServlet) throws ServletException {
    if (config == null) {
      // before init or after destroy
      if (newServlet == null) {
        // user stops requests
        servlet = NO_HOOK_SERVLET;
      } else {
        // user wants requests
        servlet = newServlet;
      }
    } else {
      // running
      if (newServlet == null) {
        // user stops requests
        servlet.destroy();
        servlet = NO_HOOK_SERVLET;
        servlet.init(config);
      } else {
        // user wants requests
        newServlet.init(config);
        servlet.destroy();
        servlet = newServlet;
      }
    }
  }
  
  private static final class NoHookServlet implements Servlet {
    public void service(
			ServletRequest req,
			ServletResponse res) throws ServletException, IOException {
      HttpServletRequest httpReq;
      HttpServletResponse httpRes;
      try {
        httpReq = (HttpServletRequest)req;
        httpRes = (HttpServletResponse)res;
      } catch (ClassCastException cce) {
	// not an http request?
	throw new ServletException("non-HTTP request or response");
      }

      // write an error message.  
      //
      // see "ServletEngine" for notes on disabling Internet Explorer's 
      // "friendly error page" option, which would hide this message.
      httpRes.setStatus(HttpServletResponse.SC_NOT_FOUND);

      httpRes.setContentType("text/html");

      String msg = 
        "<html><body>"+
        "<b>Internal error</b>: Tomcat \"hook\" not configured yet."+
        "</body></html>";
      httpRes.setContentLength(msg.length());
      PrintWriter out = httpRes.getWriter();
      out.println(msg);
    }
    
    private ServletConfig config;
    public void init(ServletConfig config) {
      this.config = config;
    }
    public ServletConfig getServletConfig() {
      return config;
    }
    public String getServletInfo() {
      return "no hook";
    }
    public void destroy() {
      config = null;
    }
  }
  
}
