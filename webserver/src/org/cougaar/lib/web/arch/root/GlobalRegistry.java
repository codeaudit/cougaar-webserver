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
package org.cougaar.lib.web.arch.root;

import java.io.IOException;
import java.util.List;

import org.cougaar.lib.web.arch.server.HttpConfig;
import org.cougaar.lib.web.arch.server.HttpsConfig;

/**
 * Interface to a global registry of servers and their
 * (globally-unique) child names.
 * <p>
 * A server can only run on "localhost", so "localhost"
 * is used when adding a name to the registry.
 * <p>
 * For example, host "bbn.com" could have HTTP support on port
 * 4321 and HTTPS support on port 8765, and contain two (internal) 
 * children, "AgentX" and "AgentY".  The <code>GlobalRegistry</code> 
 * provides support for "bbn.com" to advertise:
 * <pre><tt>
 *    ("AgentX", "http=bbn.com:4321, https=bbn.com:8765")
 *    ("AgentY", "http=bbn.com:4321, https=bbn.com:8765")
 * </tt></pre>
 * to remote servers and to find remote names, such as "AgentZ".
 * <p>
 * The names must be HTTP safe -- see RFC 1945 for details.
 */
public interface GlobalRegistry {

  /**
   * Equivalent to taking the port from
   * both HTTP[S] configs and calling the
   * <tt>configure(int,int)</tt> method.
   * <p>
   * If a parameter is null then it is treated as
   * (-1).
   */
  void configure(
      HttpConfig httpConfig,
      HttpsConfig httpsConfig) throws IOException;

  /**
   * Configure the local server's HTTP and HTTP addresses.
   * <p>
   * These address should be constant for the lifetime of 
   * the server.  This should <u>only</u> be done when the 
   * registry is first created.
   */
  void configure(
      int httpPort,
      int httpsPort) throws IOException;

  /**
   * Associate the given name in the global registry with this
   * server's location.
   * <p>
   * This will remove the existing entry if one exists.
   *
   * @throws Exception if name is already in use, or other error
   */
  void register(String name) throws IOException;

  /**
   * Remove the given name from the global registry.
   *
   * @throws Exception if name not locally registered, or other error
   */
  void unregister(String name) throws IOException;

  /**
   * Find the entry that matches the globally-unique name.
   * 
   * @return the matching GlobalEntry, or null if none matches
   *
   * @throws IOException if some low-level IO error has occurred
   *
   * @see #findAll(GlobalEntry)
   */
  GlobalEntry find(String name) throws IOException;

  /**
   * Equivalent to <tt>listNames(new ArrayList())</tt>.
   */
  public List listNames() throws IOException;

  /**
   * Fill the given <tt>toList</tt> with all the names
   * in the registry.
   * <p>
   * This should only be used for debugging purposes -- 
   * it is not efficient.
   */
  public List listNames(List toList) throws IOException;
 
  /**
   * Equivalent to <tt>findAll(new ArrayList(), query)</tt>.
   */
  public List findAll(GlobalEntry query) throws IOException;

  /**
   * Given a <tt>GlobalEntry</tt> that specifies the query, find
   * all matching entries in the registry.
   * <p>
   * If <tt>query</tt> is null then all entries are collected.
   * <p>
   * This is intended to be similar to JNDI pattern-matching, based
   * upon the <tt>GlobalEntry.matches(GlobalEntry)</tt> method.
   * <p>
   * This should only be used for debugging purposes -- 
   * it is not efficient.
   *
   * @param toList list to fill with matching GlobalEntry elements, 
   *    where each entry has <tt>(x.hasWildcard() == false)</tt>
   * @param query pattern for the find
   *
   * @return toList
   *
   * @see #find(String) faster name-based lookup
   *
   * @see GlobalEntry#matches(GlobalEntry) basis for the matching
   */
  public List findAll(List toList, GlobalEntry query) throws IOException;

}

