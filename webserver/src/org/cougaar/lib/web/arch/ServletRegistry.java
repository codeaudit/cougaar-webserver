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
package org.cougaar.lib.web.arch;

import java.util.List;

import javax.servlet.Servlet;

/**
 * 
 */
public interface ServletRegistry {

  /**
   * Get a Servlet with the matching name.
   * <p>
   * A PrefixMatch will be returned if the name matches a registered
   * regular expression.  For example:<pre>
   *   register("foo", myServlet);
   *   Object o = get("foo/bar");
   *   assert (o instanceof PrefixMatch);
   *   Object oo = ((PrefixMatch) o).getValue();
   *   assert (oo == myServlet);
   * </pre>
   *
   * @return null, Servlet, or PrefixMatch
   * @see org.cougaar.lib.web.arch.util.PrefixMatch
   */
  Object get(String name);

  /**
   * List the registered names.
   */
  List listNames();

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
  void register(String name, Servlet servlet);

  /**
   * Unregister and "Servlet.destroy()" the Servlet with the
   * matching name.
   *
   * @see #register(String,Servlet)
   */
  boolean unregister(String name);

  /**
   * Unregister all Servlets that have been registered by
   * <b>this</b> instance's "register(..)" method.
   */
  void unregisterAll();

}
