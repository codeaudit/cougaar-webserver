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
package org.cougaar.lib.web.arch.root;

import java.io.IOException;
import java.util.*;

import javax.servlet.*;

import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.leaf.LeafServletRegistry;

/**
 * FIXME.
 */
public class RootServletRegistry 
implements ServletRegistry {

  /**
   * Local names.
   */
  private final List localNames;

  /**
   * Local servlet registry.
   */
  private final ServletRegistry localReg;

  /**
   * Global registry for all (name, "scheme://host:port") pairs.
   */
  private final GlobalRegistry globReg;

  /**
   */
  public RootServletRegistry(
      GlobalRegistry globReg) {
    this.globReg = globReg;

    if (globReg == null) {
      throw new NullPointerException();
    }

    localNames = new ArrayList(13);

    // for simplicity we'll reuse the leaf-reg code
    localReg = new LeafServletRegistry();
  }

  /**
   */
  public Servlet get(String name) {
    return localReg.get(name);
  }
   
  public List listNames() {
    return localReg.listNames();
  }

  public List listNames(List toList) {
    return localReg.listNames(toList);
  }

  /**
   */
  public void register(String name, Servlet servlet) {

    // take local name
    synchronized (localNames) {
      if (localNames.contains(name)) {
        throw new IllegalArgumentException(
            "Name \""+name+"\" already in local use");
      }
      localNames.add(name);
    }

    // register locally -- this should work, since
    // the name has already been taken
    try {
      localReg.register(name, servlet);
    } catch (RuntimeException re) {
      synchronized (localNames) {
        localNames.remove(name);
      }
      throw re;
    }

    // register globally
    try {
      globReg.rebind(name);
    } catch (Exception e) {
      // release locally
      try {
        localReg.unregister(name);
      } catch (RuntimeException re) {
        // ignore
      }
      synchronized (localNames) {
        localNames.remove(name);
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
    synchronized (localNames) {
      if (!(localNames.contains(name))) {
        return false;
      }
    }

    // unregister globally
    try {
      globReg.unbind(name);
    } catch (Exception e) {
      // ignore
      ret = false;
    }

    // unregister locally
    try {
      localReg.unregister(name);
    } catch (RuntimeException re) {
      // ignore
      ret = false;
    }

    // release local name
    synchronized (localNames) {
      localNames.remove(name);
    }

    return ret;
  }

  public void unregisterAll() {
    List l;
    synchronized (localNames) {
      if (localNames.isEmpty()) {
        return;
      }
      l = new ArrayList(localNames);
    }

    for (int i = 0; i < l.size(); i++) {
      String name = (String) l.get(i);

      // unregister globally
      try {
        globReg.unbind(name);
      } catch (Exception e) {
        // ignore
      }

      // unregister locally
      try {
        localReg.unregister(name);
      } catch (RuntimeException re) {
        // ignore
      }
    }

    synchronized (localNames) {
      localNames.removeAll(l);
    }
  }

}
