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
import java.net.URI;
import java.util.List;
import java.util.Set;

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
  void rebind(String encName) throws IOException;

  /**
   * Remove the given name from the global registry.
   *
   * @throws Exception if name not locally registered, or other error
   */
  void unbind(String encName) throws IOException;

  /**
   * Find the entry that matches the globally-unique name.
   * 
   * @return the matching GlobalEntry, or null if none matches
   *
   * @throws IOException if some low-level IO error has occurred
   */
  URI get(String encName, String scheme) throws IOException;

  /**
   * Fetch all encoded names with the given suffix.
   */
  Set list(String encSuffix) throws IOException;

}
