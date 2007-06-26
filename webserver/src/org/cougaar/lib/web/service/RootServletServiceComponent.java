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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.node.NodeControlService;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.root.GlobalRegistry;
import org.cougaar.lib.web.arch.root.Redirector;
import org.cougaar.lib.web.arch.root.RootServlet;
import org.cougaar.lib.web.arch.root.RootServletRegistry;
import org.cougaar.lib.web.engine.ServletEngineService;
import org.cougaar.lib.web.redirect.NamingSupport;
import org.cougaar.lib.web.redirect.ServletRedirector;
import org.cougaar.lib.web.redirect.ServletRedirectorService;
import org.cougaar.util.GenericStateModelAdapter;

/**
 * This component uses the {@link ServletEngineService} to advertise the
 * root-level {@link RootServletService}, which is used by the
 * agent-level {@link LeafServletServiceComponent}s to create the
 * agent-internal {@link org.cougaar.core.service.ServletService}.
 * <p>
 * For example, node "N" creates the new root-level RootServletService
 * for all agents.  Agent "A" obtains the RootServletService,
 * registers itself as "/$A/", and advertises the agent's internal
 * ServletService.
 * 
 * @property org.cougaar.lib.web.redirect.timeout
 *   Timeout in millseconds for "/$" remote redirect lookups, where
 *   0 indicates no timeout.  Defaults to 0.
 *
 * @see RootServletService we provide this service
 * @see ServletEngineService required engine service
 * @see ServletRedirectService optional redirector service
 */
public class RootServletServiceComponent 
extends GenericStateModelAdapter
implements Component 
{
  private ServiceBroker sb;

  private LoggingService log;
  private ServletEngineService engine;
  private ServletRedirectorService redirector;
  private ServiceBroker rootsb;
  private WhitePagesService wp;

  private String localNode;

  private GlobalRegistry globReg;

  private ServiceProvider rootSP;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void load() {
    super.load();

    // obtain services
    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);

    // required servlet engine
    engine = (ServletEngineService)
      sb.getService(this, ServletEngineService.class, null);
    if (engine == null) {
      throw new RuntimeException("Unable to obtain ServletEngineService");
    }

    // requred redirector
    redirector = (ServletRedirectorService)
      sb.getService(this, ServletRedirectorService.class, null);
    if (redirector == null) {
      throw new RuntimeException("Unable to obtain ServletRedirectorService");
    }

    // optional root-level sb
    NodeControlService ncs = (NodeControlService)
      sb.getService(this, NodeControlService.class, null);
    if (ncs != null) {
      rootsb = ncs.getRootServiceBroker();
      sb.releaseService(this, NodeControlService.class, ncs);
    }

    // optional naming service
    wp = (WhitePagesService)
      sb.getService(this, WhitePagesService.class, null);
    if (wp == null && log.isWarnEnabled()) {
      log.warn("Root servlet-service unable to obtain WhitePagesService");
    }

    // figure out which node we're in
    NodeIdentificationService nis = (NodeIdentificationService)
      sb.getService(this, NodeIdentificationService.class, null);
    if (nis != null) {
      localNode = nis.getMessageAddress().getAddress();
      sb.releaseService(this, NodeIdentificationService.class, nis);
    }

    // create our global (wp-backed) registry
    try {
      globReg = new NamingServerRegistry(wp);
    } catch (Exception e) {
      throw new RuntimeException("Unable to create naming registry", e);
    }

    // create our local path registry
    ServletRegistry rootReg;
    try {
      rootReg = new RootServletRegistry(globReg);
    } catch (Exception e) {
      throw new RuntimeException("Unable to create local registry", e);
    }

    // create our root "gateway" servlet
    Servlet rootServlet;
    try {
      rootServlet = 
        new RootServlet(
            rootReg, 
            localNode,
            new WelcomeServlet(localNode),
            new AgentsServlet(localNode, rootReg, globReg),
            new RedirectorWrapper());
    } catch (Exception e) {
      throw new RuntimeException("Unable to create root servlet", e);
    }

    // set our gateway
    try {
      engine.setGateway(rootServlet);
    } catch (Exception e) {
      throw new RuntimeException("Unable to set gateway servlet", e);
    }

    // get our naming entries
    //
    // we do this after "setGateway(..)", in case our engine requires some
    // side-effect of setting the gateway to figure out its entries.
    Map namingEntries;
    try {
      namingEntries = engine.getNamingEntries();
    } catch (Exception e) {
      throw new RuntimeException("Unable to get the naming entries map", e);
    }

    // configure the global registry with our naming entries
    try {
      globReg.configure(namingEntries);
    } catch (Exception e) {
      throw new RuntimeException("Unable to register in the name server", e);
    }

    // create and advertise our service
    this.rootSP = new RootServletServiceProviderImpl(rootReg, namingEntries);
    ServiceBroker the_sb = (rootsb == null ? sb : rootsb);
    the_sb.addService(RootServletService.class, rootSP);
  }

  public void unload() {

    // revoke our service
    if (rootSP != null) {
      ServiceBroker the_sb = (rootsb == null ? sb : rootsb);
      the_sb.revokeService(RootServletService.class, rootSP);
      rootSP = null;
    }

    // release services
    if (wp != null) {
      sb.releaseService(this, WhitePagesService.class, wp);
      wp = null;
    }
    if (engine != null) {
      sb.releaseService(this, ServletEngineService.class, engine);
      engine = null;
    }
    if (log != null) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }

    super.unload();
  }

  //
  // inner classes:
  //

  private class RedirectorWrapper implements Redirector {

    public void redirect(
        String encName,
        List options,
        HttpServletRequest req,
        HttpServletResponse res) throws ServletException, IOException {

      int status;
      Exception error = null;
      NamingSupportImpl namingSupport = null;
     
      if (redirector == null) {
        // no redirector
        status = ServletRedirector.NOT_SUPPORTED;
      } else {
        // create naming wrapper
        namingSupport = new NamingSupportImpl();

        // attempt redirect
        try {
          status = redirector.redirect(encName, options, namingSupport, req, res);
        } catch (Exception e) {
          status = ServletRedirector.OTHER_ERROR;
          error = e;
          e.printStackTrace();
        }

        if (status == ServletRedirector.REDIRECTED) {
          // success or redirector-custom error page
          return;
        }
      }

      // write error page
      int errorCode;
      String header;
      String message;
      switch (status) {
        case ServletRedirector.NOT_SUPPORTED:
          errorCode = HttpServletResponse.SC_NOT_IMPLEMENTED;
          header = "unsupported_redirect";
          message = "Unsupported redirect options: "+options;
          break;
        case ServletRedirector.NO_NAMING_ENTRIES:
          errorCode = HttpServletResponse.SC_NOT_FOUND;
          header = "agent";
          message = ("\""+encName+"\" Not Found");
          break;
        case ServletRedirector.DETECTED_LOOP:
          errorCode = HttpServletResponse.SC_NOT_FOUND;
          header = "stale_naming";
          message = 
            "Detected stale naming entries that would have resulted in a"+
            " redirect loop: "+namingSupport.getNamingEntries(encName, -1);
          break;
        default:
          errorCode = HttpServletResponse.SC_NOT_FOUND;
          header = "other_error";
          message = 
            (error == null ? "Unspecified redirect error" :
             "Redirect exception: "+error);
          break;
      }
      res.setContentType("text/plain");
      res.setStatus(errorCode);
      res.addHeader("Cougaar-error", header);
      PrintWriter out = res.getWriter();
      out.println(message);
    }

    private class NamingSupportImpl implements NamingSupport {

      private final Map cache = new HashMap();

      public Map getNamingEntries(String encName, long timeout) {
        // we keep a cache so we don't block for the same timeout for every
        // redirector attempt, plus to make sure that all our redirectors see
        // the same naming data snapshots.
        synchronized (cache) {
          Object o = cache.get(encName);
          if (o instanceof Map) {
            // found in cached
            return (Map) o;
          }
          if (o instanceof Long) {
            // check to see if our cached timeout is longer than the new one
            long t = ((Long) o).longValue();
            if (t < 0) {
              if (timeout < 0) {
                return null;
              }
            } else if (t == 0) {
              return null;
            } else {
              if (timeout < 0 || (timeout > 0 && timeout <= t)) {
                return null;
              }
            }
          }
          // do lookup
          Map m;
          try {
            m = globReg.getAll(encName, timeout);
          } catch (Exception e) {
            // either timeout or real exception
            m = null;
          }
          // convert from name->entry(type,uri) to type->uri
          if (m != null && !m.isEmpty()) {
            Map m2 = new HashMap(m.size());
            for (Iterator iter = m.values().iterator(); iter.hasNext(); ) {
              AddressEntry ae = (AddressEntry) iter.next();
              m2.put(ae.getType(), ae.getURI());
            }
            m = Collections.unmodifiableMap(m2);
          }
          // cache result
          o = m;
          if (o == null) {
            o = new Long(timeout);
          }
          cache.put(encName, o);
          // return possibly null map
          return m;
        }
      }
    }
  }

  /**
   * Service provider for our <code>RootServletService</code>.
   */
  private static class RootServletServiceProviderImpl implements ServiceProvider {

    private final ServletRegistry rootReg;
    private final int httpPort;
    private final int httpsPort;

    public RootServletServiceProviderImpl(
        ServletRegistry rootReg, Map namingEntries) {
      this.rootReg = rootReg;
      httpPort  = _extractPort(namingEntries, "http",  80);
      httpsPort = _extractPort(namingEntries, "https", 443);
    }

    private static int _extractPort(Map m, String scheme, int deflt) {
      if (m != null) {
        Object o = m.get(scheme);
        if (o instanceof URI) {
          int port = ((URI) o).getPort();
          return (port < 0 ? deflt : port);
        }
      }
      return -1;
    }

    public Object getService(ServiceBroker sb, Object req, Class cl) {
      return 
        (RootServletService.class.isAssignableFrom(cl) ? 
         (new RootServletServiceImpl()) : null);
    }

    public void releaseService(
        ServiceBroker sb, Object req, Class cl, Object svc) {
      // unregister all servlets of this service instance
      ((RootServletServiceImpl) svc).unregisterAll();
    }

    private class RootServletServiceImpl implements RootServletService {

      // List of paths registered by this requestor; typically a 
      // tiny list.
      //
      // All the methods of this impl are synchronized on this 
      // list.  Partially this is for List safety, but also
      // it makes sense that one shouldn't be able to 
      // simultaneously register and unregister a Servlet...
      private final List l = new ArrayList(3);

      public void register(String path, Servlet servlet) throws Exception {
        synchronized (l) {
          rootReg.register(path, servlet);
          l.add(path);
        }
      }

      public void unregister(String path) {
        synchronized (l) {
          rootReg.unregister(path);
          l.remove(path);
        }
      }

      public void unregisterAll() {
        // unregister all servlets
        synchronized (l) {
          for (int n = l.size() - 1; n >= 0; n--) {
            String path = (String)l.get(n);
            rootReg.unregister(path);
          }
          l.clear();
        }
      }

      public int getHttpPort() { return httpPort; }
      public int getHttpsPort() { return httpsPort; }
    }
  }
}
