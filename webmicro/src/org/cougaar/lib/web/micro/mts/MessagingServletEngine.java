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

import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.core.agent.service.MessageSwitchService;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.lib.web.engine.ServletEngineRegistryService;
import org.cougaar.lib.web.engine.ServletEngineService;
import org.cougaar.lib.web.micro.base.ServletEngine;
import org.cougaar.lib.web.micro.base.ServletEngineImpl;
import org.cougaar.util.Arguments;
import org.cougaar.util.GenericStateModelAdapter;

/**
 * This component loads a message-transport-backed servlet engine that
 * listens for incoming {@link MessagingTunnel} servlet requests.
 *
 * @property org.cougaar.lib.web.micro.mts.engine.nagle=1000
 *   MTS-backed servlet engine response OutputStream buffering delay.
 *   See {@link OutputPipe}.
 */
public class MessagingServletEngine
extends GenericStateModelAdapter
implements Component 
{

  private ServiceBroker sb;

  private LoggingService log;
  private ThreadService threadService;
  private MessageSwitchService messageSwitch;

  private ServletEngineService ses;
  private ServletEngineRegistryService sers;
  private ServiceProvider ses_sp;

  private Arguments args = Arguments.EMPTY_INSTANCE;

  private ServletEngine engine;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void setParameter(Object o) {
    args = new Arguments(o);
  }

  public void load() {
    super.load();

    // parse args
    String prefix = "org.cougaar.lib.web.micro.mts.engine.";
    long nagle = 
      args.getLong("nagle", SystemProperties.getLong(prefix+".nagle", 1000));

    // obtain services
    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);
    threadService = (ThreadService)
      sb.getService(this, ThreadService.class, null);
    messageSwitch = (MessageSwitchService)
      sb.getService(this, MessageSwitchService.class, null);

    // figure out which node we're in
    String localNode = null;
    NodeIdentificationService nis = (NodeIdentificationService)
      sb.getService(this, NodeIdentificationService.class, null);
    if (nis != null) {
      localNode = nis.getMessageAddress().getAddress();
      sb.releaseService(this, NodeIdentificationService.class, nis);
    }

    // create naming entries
    String encNode = (localNode == null ? null : encode(localNode));
    final Map namingEntries = 
      (encNode == null ? null :
       Collections.singletonMap("mts_tunnel", URI.create("node:///"+encNode)));

    // create mts listener
    MessagingServerFactory server_factory = 
      new MessagingServerFactory(log, threadService, messageSwitch, nagle);
    server_factory.start();

    // create engine
    engine = new ServletEngineImpl(server_factory);
    engine.configure(-1, -1, null);
    try {
      engine.start();
    } catch (Exception e) {
      throw new RuntimeException("Unable to start servlet engine", e);
    }

    if (log.isInfoEnabled()) {
      log.info("Started MTS servlet engine");
    }

    // create and advertise our service
    ses = new ServletEngineService() {
      public void setGateway(Servlet s) throws ServletException {
        engine.setGateway(s);
      }
      public Map getNamingEntries() {
        return namingEntries;
      }
    };
    sers = (ServletEngineRegistryService)
      sb.getService(this, ServletEngineRegistryService.class, null);
    if (sers != null) {
      // share registry
      sers.add(ses);
    } else {
      // just us
      final Class cl = ServletEngineService.class;
      ses_sp = new ServiceProvider() {
        public Object getService(ServiceBroker sb, Object req, Class scl) {
          return (cl.isAssignableFrom(scl) ? ses : null);
        }
        public void releaseService(
            ServiceBroker sb, Object req, Class scl, Object svc) {
        }
      };
      sb.addService(cl, ses_sp);
    }
  }

  public void unload() {
   // remove/revoke our service
    if (ses != null) {
      if (sers != null) {
        sers.remove(ses);
        sers = null;
      } else if (ses_sp != null) {
        sb.revokeService(ServletEngineService.class, ses_sp);
        ses_sp = null;
      }
      ses = null;
    }

    // stop engine
    if (engine != null) {
      try {
        engine.stop();
      } catch (Exception e) {
        throw new RuntimeException("Unable to stop server", e);
      }
      engine = null;
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
    if (log != null) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }

    super.unload();
  }

  private static final String encode(String raw) {
    try {
      return URLEncoder.encode(raw, "UTF-8");
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("Invalid name: "+raw, uee);
    }
  }
}
