/*
 * <copyright>
 *  Copyright 2000-2003 BBNT Solutions, LLC
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

import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;
import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.util.DummyServletConfig;
import org.cougaar.lib.web.arch.util.PrefixTable;
import org.cougaar.lib.web.arch.util.PrefixTableImpl;
import org.cougaar.lib.web.arch.util.SynchronizedServlet;

/**
 * Registry for leaf paths.
 */
public class LeafServletRegistry 
implements ServletRegistry {

  /**
   * Table of (String, Servlet).
   */
  private final PrefixTable table = new PrefixTableImpl();

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

    // init with dummy config
    try {
      servlet.init(DummyServletConfig.getInstance());
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
