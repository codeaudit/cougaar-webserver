/*
 * <copyright>
 *  
 *  Copyright 2000-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.redirect;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.service.LoggingService;
import org.cougaar.util.Arguments;
import org.cougaar.util.GenericStateModelAdapter;

/**
 * This component is a standard {@link ServletRedirectorService} that emits a
 * client-side HTTP redirect.
 *
 * @property org.cougaar.lib.web.redirect.naming_timeout=30000
 *   Timeout in millseconds for "/$" remote redirect lookups, where
 *   0 indicates no timeout.
 */
public class HttpServletRedirector
extends GenericStateModelAdapter
implements Component
{

  protected ServiceBroker sb;

  protected LoggingService log;

  private ServletRedirectorService srs;
  private ServletRedirectorRegistryService srrs;
  private ServiceProvider srs_sp;

  protected Arguments args = Arguments.EMPTY_INSTANCE;
  protected long naming_timeout;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void setParameter(Object o) {
    args = new Arguments(o);
  }

  public void load() {
    super.load();

    // parse args
    String prefix = "org.cougaar.lib.web.redirect.";
    naming_timeout = 
      args.getLong(
          "naming_timeout",
          SystemProperties.getLong(prefix+"naming_timeout", 30000));

    // get services
    log = (LoggingService) sb.getService(this, LoggingService.class, null);

    // create and advertise our service
    srs = new ServletRedirectorService() {
      public int redirect(
          String encName, List options,
          NamingSupport namingSupport,
          HttpServletRequest req, HttpServletResponse res
          ) throws ServletException, IOException {
        return HttpServletRedirector.this.redirect(
            encName, options, namingSupport, req, res);
      }
      public String toString() {
        return HttpServletRedirector.this.toString();
      }
    };
    srrs = (ServletRedirectorRegistryService)
      sb.getService(this, ServletRedirectorRegistryService.class, null);
    if (srrs != null) {
      // share registry
      if (log.isInfoEnabled()) {
        log.info("Adding redirector to registry");
      }
      srrs.add(srs);
    } else {
      // just us
      if (log.isInfoEnabled()) {
        log.info("Advertising a new redirect service");
      }
      final Class cl = ServletRedirectorService.class;
      srs_sp = new ServiceProvider() {
        public Object getService(ServiceBroker sb, Object req, Class scl) {
          return (cl.isAssignableFrom(scl) ? srs : null);
        }
        public void releaseService(
            ServiceBroker sb, Object req, Class scl, Object svc) {
        }
      };
      sb.addService(cl, srs_sp);
    }
  }

  public void unload() {
    // remove/revoke our service
    if (srs != null) {
      if (srrs != null) {
        srrs.remove(srs);
        srrs = null;
      } else if (srs_sp != null) {
        sb.revokeService(ServletRedirectorService.class, srs_sp);
        srs_sp = null;
      }
      srs = null;
    }

    // release the logger
    if (log != null) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }

    super.unload();
  }

  protected boolean isSupported(String s) {
    return ("http_redirect".equals(s) || "^".equals(s));
  }

  protected int redirect(
      String encName,
      List options,
      NamingSupport namingSupport,
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // see if we're enabled
    if (options != null) {
      boolean is_supported = false;
      for (int i = 0; i < options.size(); i++) {
        String s = (String) options.get(i);
        if (isSupported(s)) {
          is_supported = true;
          break;
        }
      }
      if (!is_supported) {
        return ServletRedirector.NOT_SUPPORTED;
      }
    }

    // do the naming lookup
    Map uris = namingSupport.getNamingEntries(encName, naming_timeout);
    if (uris == null) {
      uris = Collections.EMPTY_MAP;
    }

    // see if the remote target supports our scheme
    String scheme = req.getScheme();
    URI uri = (URI) uris.get(scheme);
    if (uri == null) {
      // check for protocol switch
      scheme =
        ("http".equals(scheme) ? "https" :
         "https".equals(scheme) ? "http" :
         null);
      uri = (URI) uris.get(scheme);
      if (uri == null) {
        // no matching naming service entries
        return ServletRedirector.NO_NAMING_ENTRIES;
      }
    }

    String host = uri.getHost();
    int port = uri.getPort();
    String basePath = uri.getPath();

    // check for a trivial loop back to ourself
    String localHost = req.getServerName();
    int localPort = req.getServerPort();
    if ((host == null ? localHost == null : host.equals(localHost)) &&
        (port == localPort)) {
      return ServletRedirector.DETECTED_LOOP;
    }

    // create path with correct contextPath.
    // e.g. from local "/foo/$bar" to remote "/qux/$bar"
    String path = req.getRequestURI();
    String contextPath = req.getContextPath();
    if (contextPath != null && contextPath.length() > 0) {
      path = path.substring(contextPath.length());
    }
    if (basePath != null && basePath.length() > 0) {
      path = basePath + path;
    }

    // get query string, preserve as-is
    String queryString = req.getQueryString();
    queryString = (queryString == null ? "" : ("?"+queryString));

    // create the new location string
    String location = 
      scheme+"://"+
      host+":"+port+
      path+
      queryString;

    // redirect the request to the remote location
    doRedirect(encName, location, req, res);

    return ServletRedirector.REDIRECTED;
  }

  /**
   * Do the redirect.
   * <p>
   * A subclass could instead tunnel, etc.
   */
  protected void doRedirect(
      String encName,
      String location,
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    if (log.isInfoEnabled()) {
      log.info("Redirecting request for "+encName+" to "+location);
    }

    // encode for redirect -- typically a no-op
    String encLoc = res.encodeRedirectURL(location);

    res.sendRedirect(encLoc);
  }

}
