/*
 * <copyright>
 *  Copyright 2000-2001 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */
package org.cougaar.lib.web.service;

import java.util.*;

import javax.servlet.Servlet;

import org.cougaar.core.component.*;
import org.cougaar.core.servlet.ServletService;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.leaf.*;

/**
 * A root-level (Node) <code>Component</code> that adds a
 * <code>ServiceProvider</code> for <code>ServletService</code>, 
 * which is be used to register <code>Servlet</code>s.
 * <p>
 * For example, Node "N" can create a new root-level ServletService 
 * its children.  When a child, such as agent "A", requests
 * ServletService it will be able to register itself as "/$A/".
 * <p>
 * This Component overrides the parent's ServletService for all
 * Components at or below this Component's insertion point.
 *
 * @see ServletService
 */
public class LeafServletServiceComponent 
extends org.cougaar.util.GenericStateModelAdapter
implements Component 
{
  private ServiceBroker sb;

  // from parameter:
  private String name;

  private ServletService rootServletService;
  private ServiceProvider leafSP;

  public void setBindingSite(BindingSite bs) {
    // only care about the service broker
    this.sb = bs.getServiceBroker();
  }

  /**
   * Expecting a single String for the leaf's name.
   */
  public void setParameter(Object o) {
    if (!(o instanceof String)) {
      throw new IllegalArgumentException(
          "Expecting a \"String\" parameter, not \""+
          ((o != null) ? o.getClass().getName() : "null")+
          "\"");
    }
    String rawName = (String) o;

    // The name is encoded for HTTP safety.
    //
    // This is done to protect raw-names such as "x y",
    // which would produce invalid "../$x y/.." URLs.
    this.name = java.net.URLEncoder.encode(rawName);
  }

  public void load() {
    super.load();

    // get the (root) servlet service
    rootServletService = (ServletService)
      sb.getService(this, ServletService.class, null);
    if (rootServletService == null) {
      throw new RuntimeException(
          "Leaf servlet-service unable to"+
          " obtain (root) ServletService");
    }

    // create and advertise our service
    // 
    // NOTE: this overrides the rootServletService
    this.leafSP = new LeafServletServiceProviderImpl();
    sb.addService(ServletService.class, leafSP);
  }

  //
  // FIXME implement "suspend()"
  //

  public void unload() {

    // revoke our service
    if (leafSP != null) {
      sb.revokeService(ServletService.class, leafSP);
      leafSP = null;
    }

    // release the root servlet service
    if (rootServletService != null) {
      sb.releaseService(
          this, ServletService.class, rootServletService);
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

    private ServletRegistry leafReg;

    public LeafServletServiceProviderImpl() {
      try {
        configure();
      } catch (Exception e) {
        throw new RuntimeException(
            "Unable to register \""+name+"\" for Servlet service: "+
            e.getMessage());
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

    private void configure() throws Exception {

      // create a servlet and registry
      this.leafReg = 
        new LeafServletRegistry();
      Servlet unknownPathServlet = 
        new UnknownLeafPathServlet();
      LeafServlet leafServlet = 
        new LeafServlet(
            leafReg, 
            unknownPathServlet);

      // register name
      rootServletService.register(name, leafServlet);

      // get our own service
      ServletService ss = 
        new LeafServletServiceImpl();

      // add our own "/list" Servlet to display the 
      //   contents of the leafReg
      Servlet listServlet = 
        new ListRegistryServlet(
            leafReg);
      ss.register("/list", listServlet);

      // redirect "/agents" back to the root
      Servlet agentsServlet =
        new LeafToRootRedirectServlet();
      ss.register("/agents", agentsServlet);
    }

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

        /** prefix the path with the "/$name". */
        private String prefixPath(String path) {
          path = path.trim();
          if (path.length() <= 1) {
            throw new IllegalArgumentException(
                "Must specify a non-empty path, not \""+path+"\"");
          }
          if (path.charAt(0) != '/') {
            throw new IllegalArgumentException(
                "Path \""+path+"\" must start with \"/\"");
          }
          return "/$"+name+path;
        }

        public void register(
            String path,
            Servlet servlet) throws Exception {
          String fullpath = prefixPath(path);
          synchronized (l) {
            leafReg.register(fullpath, servlet);
            l.add(path);
          }
        }

        public void unregister(
            String path) {
          String fullpath = prefixPath(path);
          synchronized (l) {
            leafReg.unregister(fullpath);
            l.remove(path);
          }
        }

        public void unregisterAll() {
          // unregister all servlets
          synchronized (l) {
            for (int n = l.size() - 1; n >= 0; n--) {
              String path = (String)l.get(n);
              String fullpath = prefixPath(path);
              leafReg.unregister(fullpath);
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
}
