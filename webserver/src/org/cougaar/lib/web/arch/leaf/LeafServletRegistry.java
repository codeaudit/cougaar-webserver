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
package org.cougaar.lib.web.arch.leaf;

import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.util.PrefixTable;
import org.cougaar.lib.web.arch.util.PrefixTableImpl;
import org.cougaar.lib.web.arch.util.SynchronizedServlet;

/**
 * A registry for leaf servlet paths.
 */
public class LeafServletRegistry 
implements ServletRegistry {

  /**
   * Table of (String, Servlet).
   */
  private final PrefixTable table = new PrefixTableImpl();

  private ServletConfig config;

  /**
   */
  public LeafServletRegistry() {
  }

  /**
   * Get a Servlet with the matching name.
   */
  public Object get(String name) {
    return table.match(name);
  }

  /**
   * Get the registered paths.
   */
  public List listNames() {
    return table.list();
  }

  public void init(ServletConfig config) {
    this.config = config;
  }

  public ServletConfig getServletConfig() {
    return config;
  }

  /**
   * Register a (name, servlet) for future "get(name)" requests.
   * <p>
   * The name must be HTTP-safe -- see RFC 1945 for details.
   * <p>
   * If the same Servlet is registered multiple times, or
   * multiple threads attempt the simultaneous register
   * and unregister of a Servlet, then the order of
   * "Servlet.init()" and "Servlet.destroy()" is not
   * defined.
   *
   * @see #unregister(String)
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
    
    // add
    if (!table.add(name, servlet)) {
      throw new IllegalArgumentException(
          "Name \""+name+
          "\" is already in use by another Servlet");
    }
  }

  /**
   * Unregister and "Servlet.destroy()" the Servlet with the
   * matching name.
   *
   * @see #register
   */
  public boolean unregister(String name) {
    Servlet servlet = (Servlet) table.remove(name);

    if (servlet == null) {
      // no such name
      return false;
    }

    // un-init
    try {
      servlet.destroy();
    } catch (RuntimeException re) {
      // ignore
    }
    
    return true;
  }

  public void unregisterAll() {
    List l = table.removeAll();

    for (int i = 0; i < l.size(); i++) {
      Servlet servlet = (Servlet) l.get(i);
      // un-init
      try {
        servlet.destroy();
      } catch (RuntimeException re) {
        // ignore
      }
    }
  }
}
