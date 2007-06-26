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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;

import org.cougaar.core.component.Component;
import org.cougaar.core.component.NullService;
import org.cougaar.core.component.Service;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.ServletService;
import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.leaf.LeafServlet;
import org.cougaar.lib.web.arch.leaf.LeafServletRegistry;
import org.cougaar.lib.web.arch.leaf.LeafToRootRedirectServlet;
import org.cougaar.util.GenericStateModelAdapter;

/**
 * This component advertises the agent-internal {@link ServletService}, based
 * upon the node-level {@link RootServletService}.
 * <p>
 * This component also blocks the RootServletService, since agent components
 * should use the ServletService.
 */
public class LeafServletServiceComponent 
extends GenericStateModelAdapter
implements Component 
{
  private ServiceBroker sb;

  private String encName;
  private RootServletService rootServletService;
  private LeafServletServiceProviderImpl leafSP;
  private RootBlockerSP rootBlockerSP;

  public void setServiceBroker(ServiceBroker sb) {
    this.sb = sb;
  }

  public void setParameter(Object o) {
    // ignore old-style passing of the "encName"
  }

  public void load() {
    super.load();

    // get our agent's name
    AgentIdentificationService ais = (AgentIdentificationService)
      sb.getService(this, AgentIdentificationService.class, null);
    if (ais == null) {
      throw new RuntimeException(
          "Unable to obtain the agent identification service");
    }
    MessageAddress addr = ais.getMessageAddress();
    sb.releaseService(this, AgentIdentificationService.class, ais);

    // encode the agent name for HTTP safety.
    //
    // This is done to protect raw-names such as "x y",
    // which would produce invalid "../$x y/.." URLs.
    try {
      String rawName = addr.getAddress();
      this.encName = URLEncoder.encode(rawName, "UTF-8");
    } catch (Exception e) {
      throw new RuntimeException("Invalid name \""+addr+"\"", e);
    }

    // get the (root) servlet service
    if (rootServletService == null) {
      rootServletService = (RootServletService)
        sb.getService(this, RootServletService.class, null);
      if (rootServletService == null) {
        throw new RuntimeException(
            "Leaf servlet-service unable to obtain RootServletService");
      }
    }

    // create and advertise our service
    if (leafSP == null) {
      this.leafSP = new LeafServletServiceProviderImpl();
      leafSP.start();
      sb.addService(ServletService.class, leafSP);
    }

    // block the RootServletService
    if (rootBlockerSP == null) { 
      rootBlockerSP = new RootBlockerSP();
      sb.addService(RootServletService.class, rootBlockerSP);
    }
  }

  public void suspend() {
    super.suspend();
    if (leafSP != null) {
      leafSP.stop();
    }
  }

  public void resume() {
    if (leafSP != null) {
      leafSP.start();
    }
    super.resume();
  }

  public void unload() {
    // unblock the RootServletService
    if (rootBlockerSP != null) { 
      sb.revokeService(RootServletService.class, rootBlockerSP);
      rootBlockerSP = null;
    }

    // revoke our service
    if (leafSP != null) {
      leafSP.stop();
      sb.revokeService(ServletService.class, leafSP);
      leafSP = null;
    }

    // release the root servlet service
    if (rootServletService != null) {
      sb.releaseService(
          this, RootServletService.class, rootServletService);
      rootServletService = null;
    }

    super.unload();
  }

  //
  // private utility methods and classes:
  //

  /**
   * Service provider for our <code>ServletService</code>.
   */
  private class LeafServletServiceProviderImpl
  implements ServiceProvider {

    private final ServletRegistry leafReg;
    private final LeafServlet leafServlet;
    private boolean isRegistered;

    public LeafServletServiceProviderImpl() {
      // create a servlet and registry
      this.leafReg = 
        new LeafServletRegistry();
      Servlet unknownPathServlet = 
        new UnknownLeafPathServlet(encName);
      this.leafServlet = 
        new LeafServlet(
            leafReg, 
            unknownPathServlet);

      // get our own service
      ServletService ss = 
        new LeafServletServiceImpl();

      // add our own "/list" Servlet to display the 
      //   contents of the leafReg
      try {
        Servlet listServlet = 
          new ListRegistryServlet(
              leafReg,
              encName);
        ss.register("/list", listServlet);
      } catch (Exception e) {
        // shouldn't happen
        throw new RuntimeException(
            "Unable to register \"/list\" servlet", e);
      }

      // redirect "/agents" back to the root
      try {
        Servlet agentsServlet =
          new LeafToRootRedirectServlet();
        ss.register("/agents", agentsServlet);
      } catch (Exception e) {
        // shouldn't happen
        throw new RuntimeException(
            "Unable to register \"/agents\" servlet", e);
      }

      // wait until "start()" to register
    }
    
    public void start() {
      if (!(isRegistered)) {
        try {
          // register encoded name
          rootServletService.register(encName, leafServlet);
        } catch (Exception e) {
          throw new RuntimeException(
              "Unable to register \""+encName+"\" for Servlet service",
              e);
        }
        isRegistered = true;
      }
    }

    public void stop() {
      if (isRegistered) {
        try {
          // unregister encoded name
          rootServletService.unregister(encName);

          // FIXME wait until all child servlets have halted
        } finally {
          isRegistered = false;
        }
      }
    }

    public Object getService(
        ServiceBroker sb, 
        Object requestor, 
        Class serviceClass) {
      // create a new service instance
      if (serviceClass == ServletService.class) {
        return new LeafServletServiceImpl();
      } else {
        throw new IllegalArgumentException(
            "ServletService does not provide a service for: "+
            serviceClass);
      }
    }

    public void releaseService(
        ServiceBroker sb, 
        Object requestor, 
        Class serviceClass, 
        Object service)  {
      // unregister all servlets of this service instance
      if (!(service instanceof LeafServletServiceImpl)) {
        throw new IllegalArgumentException(
            "ServletService unable to release service: "+
            ((service != null) ? service.getClass().toString() : "null"));
      }
      LeafServletServiceImpl lssi =
        (LeafServletServiceImpl)service;
      lssi.unregisterAll();
    }

    //
    // private utility methods and classes:
    //

    /**
     * Leaf's ServletService implementation.
     */
    private class LeafServletServiceImpl
      implements ServletService {

        // List of paths registered by this requestor; typically a 
        // tiny list.
        //
        // All the methods of this impl are synchronized on this 
        // list.  Partially this is for List safety, but also
        // it makes sense that one shouldn't be able to 
        // simultaneously register and unregister a Servlet...
        private final List l = new ArrayList(3);

        private String trimPath(String path) {
          String p = path.trim();
          if (p.length() <= 1) {
            throw new IllegalArgumentException(
                "Must specify a non-empty path, not \""+path+"\"");
          }
          if (p.charAt(0) != '/') {
            throw new IllegalArgumentException(
                "Path \""+path+"\" must start with \"/\"");
          }
          return p;
        }

        public void register(
            String path,
            Servlet servlet) throws Exception {
          String p = trimPath(path);
          synchronized (l) {
            leafReg.register(p, servlet);
            l.add(p);
          }
        }

        public void unregister(
            String path) {
          String p = trimPath(path);
          synchronized (l) {
            leafReg.unregister(p);
            l.remove(p);
          }
        }

        public void unregisterAll() {
          // unregister all servlets
          synchronized (l) {
            for (int n = l.size() - 1; n >= 0; n--) {
              String p = (String) l.get(n);
              leafReg.unregister(p);
            }
            l.clear();
          }
        }

        public int getHttpPort() {
          return rootServletService.getHttpPort();
        }

        public int getHttpsPort() {
          return rootServletService.getHttpsPort();
        }
      }
  }

  private static final class RootBlockerSP
    implements ServiceProvider {
      private final Service NULL = new NullService() {};
      public Object getService(
          ServiceBroker sb, Object requestor, Class serviceClass) {
        if (RootServletService.class.isAssignableFrom(serviceClass)) {
          return NULL; // service blocker!
        } else {
          return null;
        }
      }
      public void releaseService(
          ServiceBroker sb, Object requestor, 
          Class serviceClass, Object service) {
      }
    }
}
