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
import java.util.ArrayList;
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
import org.cougaar.util.CSVUtility;
import org.cougaar.util.GenericStateModelAdapter;

/**
 * This component advertises the {@link ServletRedirectRegistryService}.
 * <p>
 * The servlet request URL can optionally specify redirector filters,
 * e.g.:<br>
 * &nbsp;&nbsp;  http://localhost:8800/$(http_redirect,foo)MyAgent/bar<br>
 * where the options are:<br>
 * &nbsp;&nbsp;  [http_redirect, foo]<br>
 * In this example, these explicit redirectors will be attempted in that
 * specified order.  If no redirectors are specified then the "default_order"
 * list defined in this class will be used, which defaults to null.  If the
 * list is null then all loaded redirectors will be attempted in the order in
 * which they were loaded.
 *
 * @property org.cougaar.lib.web.redirect.default_order=null
 *   Default servlet redirector attempt order, defaults to null.
 */
public class ServletRedirectorRegistry
extends GenericStateModelAdapter
implements Component
{

  private ServiceBroker sb;

  protected LoggingService log;

  private Arguments args = Arguments.EMPTY_INSTANCE;
  private List default_order = null;

  private ServiceProvider srs_sp;
  private ServiceProvider srrs_sp;

  private final Object lock = new Object();
  private List redirectors = Collections.EMPTY_LIST;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void setParameter(Object o) {
    args = new Arguments(o);
  }

  public void load() {
    super.load();

    // parse parameters
    String prefix = "org.cougaar.lib.web.redirect.";
    String s_order = 
      args.getString(
          "default_order",
          SystemProperties.getProperty(prefix+"default_order"));
    if (s_order != null && !"null".equals(s_order)) {
      List l = CSVUtility.parseToList(s_order);
      default_order =  Collections.unmodifiableList(l);
    }

    // get services
    log = (LoggingService) sb.getService(this, LoggingService.class, null);

    // advertise registry
    final ServletRedirectorRegistry thiz = this;
    final ServletRedirectorRegistryService srrs =
      new ServletRedirectorRegistryService() {
        public void add(ServletRedirector redirector) { thiz.add(redirector); }
        public void remove(ServletRedirector redirector) { thiz.remove(redirector); }
      };
    final Class srrs_cl = ServletRedirectorRegistryService.class;
    srrs_sp = new ServiceProvider() {
      public Object getService(ServiceBroker sb, Object req, Class scl) {
        return (srrs_cl.isAssignableFrom(scl) ? srrs : null);
      }
      public void releaseService(
          ServiceBroker sb, Object req, Class scl, Object svc) {
      }
    };
    sb.addService(srrs_cl, srrs_sp);

    // advertise redirector
    final ServletRedirectorService srs =
      new ServletRedirectorService() {
        public int redirect(
            String encName,
            List options,
            NamingSupport namingSupport,
            HttpServletRequest req,
            HttpServletResponse res) throws ServletException, IOException {
          return thiz.redirect(encName, options, namingSupport, req, res);
        }
      };
    final Class srs_cl = ServletRedirectorService.class;
    srs_sp = new ServiceProvider() {
      public Object getService(ServiceBroker sb, Object req, Class scl) {
        return (srs_cl.isAssignableFrom(scl) ? srs : null);
      }
      public void releaseService(
          ServiceBroker sb, Object req, Class scl, Object svc) {
      }
    };
    sb.addService(srs_cl, srs_sp);
  }

  public void unload() {
    // revoke redirector
    if (srs_sp != null) {
      Class srs_cl = ServletRedirectorService.class;
      sb.revokeService(srs_cl, srs_sp);
      srs_sp = null;
    }

    // revoke registry
    if (srrs_sp != null) {
      Class srrs_cl = ServletRedirectorRegistryService.class;
      sb.revokeService(srrs_cl, srrs_sp);
      srrs_sp = null;
    }

    // release services
    if (log != null) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }

    super.unload();
  }

  // copy-on-write
  private void add(ServletRedirector redirector) {
    synchronized (lock) {
      if (redirectors.contains(redirector)) {
        return;
      }
      List l = new ArrayList(redirectors);
      l.add(redirector);
      redirectors = Collections.unmodifiableList(l);
    }
  }
  private void remove(ServletRedirector redirector) {
    synchronized (lock) {
      if (!redirectors.contains(redirector)) {
        return;
      }
      List l = new ArrayList(redirectors);
      l.remove(redirector);
      redirectors = Collections.unmodifiableList(l);
    }
  }
  private List getRedirectors() {
    synchronized (lock) {
      return redirectors;
    }
  }

  private int redirect(
      String encName,
      List orig_options,
      NamingSupport namingSupport,
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    List options = (orig_options == null ? default_order : orig_options);

    int ret = ServletRedirector.NOT_SUPPORTED;
    if (options != null && options.isEmpty()) {
      // do nothing
      return ret;
    }
    List l = getRedirectors();

    if (options == null) {
      // if no options are specified then we run all loaded redirectors in the
      // order in which they were added.
      if (log.isDebugEnabled()) {
        log.debug("attempt all redirectors in load order");
      }
      return attempt(encName, namingSupport, req, res, l, null, ret);
    }

    // call our redirectors in the specified options order
    if (log.isDebugEnabled()) {
      log.debug("attempt options: "+options);
    }
    for (int i = 0, n = options.size(); i < n; i++) {
      List single_opt = Collections.singletonList(options.get(i));
      ret = attempt(encName, namingSupport, req, res, l, single_opt, ret);
      if (ret == ServletRedirector.REDIRECTED) {
        break;
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("    returning "+ret);
    }
    return ret;
  }

  private int attempt(
      String encName,
      NamingSupport namingSupport,
      HttpServletRequest req,
      HttpServletResponse res,
      List l,
      List opts,
      int prior_ret) throws ServletException, IOException {
    int ret = prior_ret;
    for (int i = 0, n = l.size(); i < n; i++) {
      ServletRedirector ri = (ServletRedirector) l.get(i);
      int status = ri.redirect(encName, opts, namingSupport, req, res);

      if (log.isDebugEnabled()) {
        log.debug("  redir("+opts+") == "+status+" from "+ri);
      }

      // update our return value
      //
      // we keep a "ret" value that is updated in order of:
      //   NOT_SUPPORTED 
      //   NO_NAMING_ENTRIES 
      //   DETECTED_LOOP or OTHER_ERROR 
      //   REDIRECTED
      // This ensures that we return the most significant result, not just the
      // last one we tried.
      switch (status) {
        case ServletRedirector.REDIRECTED:
          // success, we're done
          return ServletRedirector.REDIRECTED;
        case ServletRedirector.NOT_SUPPORTED:
          // leave ret as-is
          break;
        case ServletRedirector.NO_NAMING_ENTRIES:
          // don't change ret if it's a LOOP or ERROR
          if (ret == ServletRedirector.NOT_SUPPORTED) {
            ret = ServletRedirector.NO_NAMING_ENTRIES;
          }
          break;
        case ServletRedirector.DETECTED_LOOP:
          // save
          ret = ServletRedirector.DETECTED_LOOP;
          break;
        default:
          // save
          ret = ServletRedirector.OTHER_ERROR;
      }
    }
    return ret;
  }
}
