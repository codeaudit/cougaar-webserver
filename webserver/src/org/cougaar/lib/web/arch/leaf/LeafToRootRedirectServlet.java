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
package org.cougaar.lib.web.arch.leaf;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet that redirects "/$[~]name/foo" to "/foo".
 * <p>
 * This can be used to a bounce a leaf-level request back to 
 * the root-level, assuming that the request is (logically) for
 * the root-level.  This is very handy when the client doesn't
 * know which server the leaf happens to be located at and wants 
 * to rely upon redirects.  In other words, if leaf "X" is 
 * configured to bounce "/foo" back to the root, it allows the 
 * user to easily phrase:<pre>
 *    "Wherever <i>/$[~]name</i> happens to be right now, 
 *     invoke <i>/foo</i> to its root"
 * </pre>
 * <p>
 * Note that the root shouldn't bounce this back to the leaf,
 * otherwise it's a loop!
 */
public class LeafToRootRedirectServlet 
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

    redirect(httpReq, httpRes);
  }

  /**
   * Utility method for redirect.
   */
  public final void redirect(
      HttpServletRequest httpReq,
      HttpServletResponse httpRes) throws ServletException, IOException {

    // get the "/$[~]name[/.*]"
    String path = httpReq.getRequestURI();
    // assert ((path != null) && (path.startsWith("/$")))
    int sepIdx = path.indexOf('/', 2);
    if (sepIdx < 0) {
      sepIdx = path.length();
    }
    String trimPath = path.substring(sepIdx);

    String queryString = 
      httpReq.getQueryString();

    // create the new location string
    String location = 
      httpReq.getScheme()+
      "://"+
      httpReq.getServerName()+
      ":"+
      httpReq.getServerPort()+
      trimPath+
      ((queryString != null) ? 
       ("?"+queryString) :
       (""));

    // encode for redirect -- typically a no-op
    location = httpRes.encodeRedirectURL(location);

    // redirect the request to the remote location
    httpRes.sendRedirect(location);
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
    return "leaf-to-root-redirect";
  }
  public void destroy() {
    // ignore
  }

}
