/*
 * <copyright>
 *  
 *  Copyright 2000-2008 BBNT Solutions, LLC
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
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.servlet.ComponentServlet;
import org.cougaar.lib.web.arch.root.GlobalRegistry;
import org.cougaar.lib.web.arch.root.Redirector;
import org.cougaar.lib.web.redirect.RedirectorWrapper;
import org.cougaar.lib.web.redirect.ServletRedirectorService;

/**
 * This servlet handles "/redirect" requests.
 * <p>
 * This is not the "/$" redirector -- that agent-based redirector is built into
 * the {@link org.cougaar.lib.web.arch.root.RootServlet}.  Instead, this
 * optional servlet supports similar cross-domain redirection and tunneling.
 * <p>
 * For example, if a user on host A wants to access a servlet on host C:<br>
 * &nbsp;&nbsp;<tt> http://C:8800/foo</tt><br>
 * but can only access host B, then they can use this servlet to do:<br>
 * &nbsp;&nbsp;<tt> http://B:8800/redirect/C:8800/foo</tt><br>
 * The "/foo" path, query parameters, and post data will all be tunneled.
 * <p>
 *
 * @property org.cougaar.society.xsl.param.servlet_redirector.http_tunnel=true
 * The HTTP servlet tunnel must be enabled, otherwise this servlet will
 * generate an "unsupported redirect" error page. 
 */
public class RedirectServlet extends ComponentServlet {

  private ServletRedirectorService redirector;

  public void setServletRedirectorService(ServletRedirectorService srs) {
    this.redirector = srs;
  }

  protected String getPath() {
    String ret = super.getPath();
    return (ret == null ? "/redirect" : ret);
  }

  protected void service(
      HttpServletRequest req, HttpServletResponse res
      ) throws ServletException, IOException {
    // get pathInfo
    String path = req.getPathInfo();
    if (path == null) {
      path = "";
    } else {
      path = path.trim();
      if (path.startsWith("/")) {
        path = path.substring(1).trim();
      }
    }

    // parse [target][/subpath]
    String target;
    final String subpath;
    int sep = path.indexOf('/');
    if (sep < 0) {
      target = path;
      subpath = "/";
    } else {
      target = path.substring(0, sep).trim();
      subpath = path.substring(sep).trim();
    }

    // parse uri from target
    int sep2 = target.indexOf(':');
    if (sep2 < 0) {
      res.setContentType("text/html");
      PrintWriter out = res.getWriter();
      String p = getPath();
      out.println(
          "<html><body><pre>"+
          "Expecting "+p+"/[host]:port[/path]"+
          "\n"+
          "\nFor example:"+
          "\n  http://hostA:1234/"+p+"/hostB:5678/foo?a=b&c=d"+
          "\nwill be tunneled to:"+
          "\n  http://hostB:5678/foo?a=b&c=d"+
          "\n</pre></body></html>");
      out.close();
      return;
    }
    String scheme = req.getScheme();
    String host = (sep2 == 0 ? "localhost" : target.substring(0, sep2));
    String port = target.substring(sep2+1);
    URI uri = URI.create(scheme+"://"+host+":"+port);

    // create redirector args
    AddressEntry ae = 
      AddressEntry.getAddressEntry("dummyName", uri.getScheme(), uri);
    String encName = ae.getName();
    List options = Collections.singletonList("http_tunnel");
    HttpServletRequest httpReq =
      new HttpServletRequestWrapper(req) {
        public String getRequestURI() { return subpath; }
        public String getPathInfo() { return subpath; }
        public String getServletPath() { return subpath; }
      };
    HttpServletResponse httpRes = res;

    // do redirect
    Redirector r = new RedirectorWrapper(redirector, new RegistryImpl(ae));
    r.redirect(encName, options, httpReq, httpRes);
  }

  private static class RegistryImpl implements GlobalRegistry {
    private final AddressEntry ae;
    public RegistryImpl(AddressEntry ae) { this.ae = ae; }
    public Map getAll(String encName, long timeout) {
      return 
        (encName != null && encName.equals(ae.getName()) ?
         Collections.singletonMap(ae.getName(), ae) :
         Collections.emptyMap());
    }
    public void configure(Map namingEntries) { die(); }
    public void rebind(String encName) { die(); }
    public void unbind(String encName) { die(); }
    public Set list(String encSuffix, long timeout) { die(); return null; }
    private void die() { throw new UnsupportedOperationException(); }
  }
}
