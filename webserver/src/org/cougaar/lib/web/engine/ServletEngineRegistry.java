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

package org.cougaar.lib.web.engine;

import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.util.GenericStateModelAdapter;

/**
 * This component advertises the {@link ServletEngineRegistryService}.
 */
public class ServletEngineRegistry
extends GenericStateModelAdapter
implements Component
{

  private ServiceBroker sb;

  private ServiceProvider ses_sp;
  private ServiceProvider sers_sp;

  private final Object lock = new Object();
  private List engines = Collections.EMPTY_LIST;

  private boolean configured;
  private int httpPort;
  private int httpsPort;
  private Map options;
  private boolean running;
  private Servlet gateway;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void load() {
    super.load();

    // advertise registry
    final ServletEngineRegistry thiz = this;
    final ServletEngineRegistryService sers =
      new ServletEngineRegistryService() {
        public void add(ServletEngine engine) { thiz.add(engine); }
        public void remove(ServletEngine engine) { thiz.remove(engine); }
      };
    final Class sers_cl = ServletEngineRegistryService.class;
    sers_sp = new ServiceProvider() {
      public Object getService(ServiceBroker sb, Object req, Class scl) {
        return (sers_cl.isAssignableFrom(scl) ? sers : null);
      }
      public void releaseService(
          ServiceBroker sb, Object req, Class scl, Object svc) {
      }
    };
    sb.addService(sers_cl, sers_sp);

    // advertise engine
    final ServletEngineService ses =
      new ServletEngineService() {
        public void setGateway(Servlet s) throws ServletException {
          thiz.setGateway(s);
        }
        public Map getNamingEntries() {
          return thiz.getNamingEntries();
        }
      };
    final Class ses_cl = ServletEngineService.class;
    ses_sp = new ServiceProvider() {
      public Object getService(ServiceBroker sb, Object req, Class scl) {
        return (ses_cl.isAssignableFrom(scl) ? ses : null);
      }
      public void releaseService(
          ServiceBroker sb, Object req, Class scl, Object svc) {
      }
    };
    sb.addService(ses_cl, ses_sp);
  }

  public void unload() {
    // revoke engine
    if (ses_sp != null) {
      Class ses_cl = ServletEngineService.class;
      sb.revokeService(ses_cl, ses_sp);
      ses_sp = null;
    }

    // revoke registry
    if (sers_sp != null) {
      Class sers_cl = ServletEngineRegistryService.class;
      sb.revokeService(sers_cl, sers_sp);
      sers_sp = null;
    }

    super.unload();
  }

  //
  // registry is copy-on-write
  //

  private void add(ServletEngine engine) {
    synchronized (lock) {
      if (engines.contains(engine)) {
        return;
      }
      List l = new ArrayList(engines);
      l.add(engine);
      engines = Collections.unmodifiableList(l);
    }
    if (gateway != null) {
      try {
        engine.setGateway(gateway);
      } catch (Exception e) {
        throw new RuntimeException("SetGateway failed", e);
      }
    }
  }
  private void remove(ServletEngine engine) {
    synchronized (lock) {
      if (!engines.contains(engine)) {
        return;
      }
      List l = new ArrayList(engines);
      l.remove(engine);
      engines = Collections.unmodifiableList(l);
    }
  }
  private List getEngines() {
    synchronized (lock) {
      return engines;
    }
  }

  //
  // engine is backed by registry
  //

  private void setGateway(Servlet s) throws ServletException {
    this.gateway = s;

    // set the gateway in all engines
    List l = getEngines();
    for (int i = 0; i < l.size(); i++) {
      ServletEngine ei = (ServletEngine) l.get(i);
      ei.setGateway(s);
    }
  }

  private Map getNamingEntries() {
    // ask all engines for their entries, prefer entries from first-loaded
    // engines  (e.g. if engine A and B both want to advertise "http", we'll
    // only keep A's entry).
    Map ret = new HashMap();
    List l = getEngines();
    for (int i = 0; i < l.size(); i++) {
      ServletEngine ei = (ServletEngine) l.get(i);
      Map m = ei.getNamingEntries();
      if (m == null || m.isEmpty()) continue;
      for (Iterator iter = m.entrySet().iterator(); iter.hasNext(); ) {
        Map.Entry me = (Map.Entry) iter.next();
        Object key = me.getKey();
        if (ret.containsKey(key)) continue;
        ret.put(key, me.getValue());
      }
    }
    return Collections.unmodifiableMap(ret);
  }
}
