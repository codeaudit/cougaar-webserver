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
package org.cougaar.lib.web.arch.root;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.util.SynchronizedServlet;

/**
 * FIXME.
 */
public class RootServletRegistry 
implements ServletRegistry {

  // Map<String, Servlet>
  private final Map localServlets;

  /**
   * Global registry for all (name, "scheme://host:port") pairs.
   */
  private final GlobalRegistry globReg;

  private ServletConfig config;

  /**
   */
  public RootServletRegistry(
      GlobalRegistry globReg) {
    this.globReg = globReg;

    if (globReg == null) {
      throw new NullPointerException();
    }

    localServlets = new HashMap(13);
  }

  /**
   */
  public Object get(String name) {
    synchronized (localServlets) {
      return localServlets.get(name);
    }
  }
   
  public List listNames() {
    synchronized (localServlets) {
      return new ArrayList(localServlets.keySet());
    }
  }

  public void init(ServletConfig config) {
    this.config = config;
  }

  public ServletConfig getServletConfig() {
    return config;
  }

  /**
   */
  public void register(String name, Servlet servlet) {
    if ((name == null) ||
        (servlet == null)) {
      throw new NullPointerException();
    }

    // wrap if SingleThreadModel
    if (servlet instanceof SingleThreadModel) {
      servlet = new SynchronizedServlet(servlet);
    }

    // init with config
    try {
      servlet.init(config);
    } catch (ServletException se) {
      throw new RuntimeException(
          "Unable to initialize servlet: "+
          se.getMessage());
    }
    
    // register locally
    synchronized (localServlets) {
      Object o = localServlets.put(name, servlet);
      if (o != null) {
        // un-put! somewhat wasteful
        localServlets.put(name, o);
        throw new IllegalArgumentException(
            "Name \""+name+"\" already in local use");
      }
    }

    // register globally
    try {
      globReg.rebind(name);
    } catch (Exception e) {
      // release locally
      synchronized (localServlets) {
        localServlets.remove(name);
      }
      // un-init
      try {
        servlet.destroy();
      } catch (RuntimeException re) {
        // ignore
      }
      throw new RuntimeException(
          "Unable to bind \""+name+"\" in the global registry",
          e);
    }
  }

  /**
   */
  public boolean unregister(String name) {
    boolean ret = true;

    // quick-check locally
    if (get(name) == null) {
      return false;
    }

    // unregister globally
    try {
      globReg.unbind(name);
    } catch (Exception e) {
      // ignore
      ret = false;
    }

    // unregister locally
    Servlet s;
    synchronized (localServlets) {
      s = (Servlet) localServlets.remove(name);
    }

    // un-init
    if (s != null) {
      try {
        s.destroy();
      } catch (RuntimeException re) {
        // ignore
      }
    }

    return ret;
  }

  public void unregisterAll() {
    List l = listNames();
    for (int i = 0; i < l.size(); i++) {
      String name = (String) l.get(i);
      unregister(name);
    }
  }

}
