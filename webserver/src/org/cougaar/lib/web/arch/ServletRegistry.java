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
package org.cougaar.lib.web.arch;

import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;

/**
 * A table of servlets.
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
   * Initialize with the {@link ServletConfig}.
   */
  void init(ServletConfig config);

  /**
   * Get the {@link ServletConfig} set in {@link #init}.
   */
  ServletConfig getServletConfig();

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
