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

package org.cougaar.lib.web.micro.mts;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.core.agent.service.MessageSwitchService;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.service.UIDService;
import org.cougaar.lib.web.micro.base.Connection;
import org.cougaar.lib.web.micro.base.ServletTunnel;
import org.cougaar.lib.web.redirect.NamingSupport;
import org.cougaar.lib.web.redirect.ServletRedirector;
import org.cougaar.lib.web.redirect.ServletRedirectorService;
import org.cougaar.lib.web.redirect.ServletRedirectorRegistryService;
import org.cougaar.util.Arguments;
import org.cougaar.util.GenericStateModelAdapter;

/**
 * This component loads the "mts_tunnel" servlet redirector, which tunnels
 * servlet requests through the message transport to a remote mts-engine.
 *
 * @property org.cougaar.lib.web.micro.mts.tunnel.nagle=1000
 * @property org.cougaar.lib.web.micro.mts.tunnel.naming_timeout=30000
 */
public class MessagingServletTunnel
extends GenericStateModelAdapter
implements Component
{

  private ServiceBroker sb;

  private Arguments args = Arguments.EMPTY_INSTANCE;

  private LoggingService log;
  private UIDService uids;
  private ThreadService threadService;
  private MessageSwitchService messageSwitch;

  private long naming_timeout;

  private ServletRedirectorService srs;
  private ServletRedirectorRegistryService srrs;
  private ServiceProvider srs_sp;

  private String localNode;
  private MessagingClientFactory client_factory;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void setParameter(Object o) {
    args = new Arguments(o);
  }

  public void load() {
    super.load();

    // parse args
    String prefix = "org.cougaar.lib.web.micro.mts.tunnel.";
    long nagle = 
      args.getLong("nagle", SystemProperties.getLong(prefix+"nagle", 1000));
    naming_timeout =
      args.getLong(
          "naming_timeout",
          SystemProperties.getLong(prefix+"naming_timeout", 30000));

    // obtain services
    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);
    uids = (UIDService)
      sb.getService(this, UIDService.class, null);
    threadService = (ThreadService)
      sb.getService(this, ThreadService.class, null);
    messageSwitch = (MessageSwitchService)
      sb.getService(this, MessageSwitchService.class, null);

    // figure out which node we're in
    NodeIdentificationService nis = (NodeIdentificationService)
      sb.getService(this, NodeIdentificationService.class, null);
    if (nis != null) {
      localNode = nis.getMessageAddress().getAddress();
      sb.releaseService(this, NodeIdentificationService.class, nis);
    }

    // create connection factory
    client_factory = new MessagingClientFactory(
        log, uids, threadService, messageSwitch, nagle);
    client_factory.start();

    if (log.isInfoEnabled()) {
      log.info("Started MTS servlet tunnel");
    }

    // create and advertise our service
    srs = new ServletRedirectorService() {
      public int redirect(
          String encName, List options,
          NamingSupport namingSupport,
          HttpServletRequest req, HttpServletResponse res
          ) throws ServletException, IOException {
        return MessagingServletTunnel.this.redirect(
            encName, options, namingSupport, req, res);
      }
    };
    srrs = (ServletRedirectorRegistryService)
      sb.getService(this, ServletRedirectorRegistryService.class, null);
    if (srrs != null) {
      // share registry
      srrs.add(srs);
    } else {
      // just us
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

    // stop mts listener
    if (client_factory != null) {
      client_factory.stop();
      client_factory = null;
    }

    // release services
    if (messageSwitch != null) {
      sb.releaseService(this, MessageSwitchService.class, messageSwitch);
      messageSwitch = null;
    }
    if (threadService != null) {
      sb.releaseService(this, ThreadService.class, threadService);
      threadService = null;
    }
    if (uids != null) {
      sb.releaseService(this, UIDService.class, uids);
      uids = null;
    }
    if (log != null) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }

    super.unload();
  }

  private boolean isSupported(String s) {
    return ("mts_tunnel".equals(s) || "_".equals(s));
  }

  private int redirect(
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

    // see if the remote target can receive tunnel requests
    URI uri = (URI) uris.get("mts_tunnel");
    if (uri == null) {
      return ServletRedirector.NO_NAMING_ENTRIES;
    }

    // extract remote node name
    String encNode = uri.getPath();
    if (encNode == null) {
      encNode = "";
    }
    if (encNode.length() > 0 && encNode.charAt(0) == '/') {
      encNode = encNode.substring(1);
    }
    String rawNode = decode(encNode);
    if (rawNode.length() <= 0) {
      // invalid entry, shouldn't happen
      return ServletRedirector.OTHER_ERROR;
    }
    MessageAddress addr = MessageAddress.getMessageAddress(rawNode);

    // check for loopback error, possibly due to stale naming entries.
    if (addr.equals(localNode)) {
      return ServletRedirector.DETECTED_LOOP;
    }

    // tunnel
    Map metaData = ServletTunnel.extractMetaData(req);
    Connection con = client_factory.connect(addr, metaData);
    ServletTunnel.tunnel(req, res, con);
    return ServletRedirector.REDIRECTED;
  }

  private static final String decode(String enc) {
    try {
      return URLDecoder.decode(enc, "UTF-8");
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("Invalid name: "+enc, uee);
    }
  }
}
