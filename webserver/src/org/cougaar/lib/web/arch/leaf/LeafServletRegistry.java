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
package org.cougaar.lib.web.arch.leaf;

import java.io.IOException;
import java.util.*;

import javax.servlet.*;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.DummyServletConfig;

/**
 * FIXME.
 */
public class LeafServletRegistry 
implements ServletRegistry {

  /**
   * Map of (name, Servlet).
   */
  private final Map myServlets = new HashMap();

  /**
   */
  public LeafServletRegistry() {
  }

  /**
   * Get a Servlet with the matching name.
   */
  public Servlet get(
      String name) {
    synchronized (myServlets) {
      return (Servlet) myServlets.get(name);
    }
  }

  /**
   * Equivalent to <tt>listNames(new ArrayList())</tt>.
   */
  public List listNames() {
    synchronized (myServlets) {
      return new ArrayList(myServlets.keySet());
    }
  }

  /**
   * Fill the given <code>List</code> with the currently
   * registered names.
   */
  public List listNames(List toList) {
    Set s;
    synchronized (myServlets) {
      s = myServlets.keySet();
    }
    int n = s.size();
    if (n > 0) {
      Iterator iter = s.iterator();
      do {
        toList.add(iter.next());
      } while (--n > 0);
    }
    return toList;
  }

  /**
   * Register a (name, servlet) for future "get(name)" requests.
   * <p>
   * "Servlet.init(..)" is called with the 
   * <code>DummyServletConfig</code>.
   * <p>
   * The name must be HTTP-safe -- see RFC 1945 for details.
   * <p>
   * If the same Servlet is registered multiple times, or
   * multiple threads attempt the simultaneous register
   * and unregister of a Servlet, then the order of
   * "Servlet.init()" and "Servlet.destroy()" is not
   * defined.
   *
   * @see #unregister(name)
   */
  public void register(String name, Servlet servlet) {

    if ((name == null) ||
        (servlet == null)) {
      throw new NullPointerException();
    }

    // init with dummy config
    try {
      servlet.init(DummyServletConfig.getInstance());
    } catch (ServletException se) {
      throw new RuntimeException(
          "Unable to initialize servlet: "+
          se.getMessage());
    }
    
    // wrap if SingleThreadModel
    if (servlet instanceof SingleThreadModel) {
      servlet = new SynchronizedServlet(servlet);
    }

    // add
    synchronized (myServlets) {
      Object o = myServlets.put(name, servlet);
      if (o != null) {
        // un-put! somewhat wasteful
        myServlets.put(name, o);
        throw new IllegalArgumentException(
            "Name \""+name+
            "\" is already in use by another Servlet");
      }
    }
  }

  /**
   * Unregister and "Servlet.destroy()" the Servlet with the
   * matching name.
   *
   * @see #register
   */
  public boolean unregister(String name) {
    Servlet servlet;
    synchronized (myServlets) {
      servlet = (Servlet) myServlets.remove(name);
    }
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
    List l;
    synchronized (myServlets) {
      l = new ArrayList(myServlets.values());
      myServlets.clear();
    }

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

  /**
   * Simple wrapper for Servlets that implement
   * <code>SingleThreadModel</code> -- <b>note</b> that
   * if a Servlet is registered under multiple names then
   * this will only synchronize per <i>name</i>.
   */
  private static final class SynchronizedServlet implements Servlet {
    private final Servlet s;
    public SynchronizedServlet(Servlet s) {
      this.s = s;
    }
    // synchronize on service requests.
    public void service(
        ServletRequest req,
        ServletResponse res) throws ServletException, IOException {
      synchronized (this) {
        s.service(req, res);
      }
    }
    // delegate the rest
    public void init(ServletConfig config) throws ServletException {
      s.init(config);
    }
    public ServletConfig getServletConfig() {
      return s.getServletConfig();
    }
    public String getServletInfo() {
      return s.getServletInfo();
    }
    public void destroy() {
      s.destroy();
    }
    public String toString() {
      return s.toString();
    }
  }
}
