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

  /**
   * Default servlet when no hook servlet has been configured.
   */
  private static final class NoHookServlet implements Servlet {
    public void service(ServletRequest req, ServletResponse res)
      throws ServletException, IOException {
      if (!(req instanceof HttpServletRequest) ||
          !(res instanceof HttpServletResponse)) {
        throw new ServletException("non-HTTP request or response");
      }
      HttpServletResponse httpRes = (HttpServletResponse) res;

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
