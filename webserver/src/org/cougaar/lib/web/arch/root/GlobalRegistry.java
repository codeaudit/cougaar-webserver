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

import java.io.IOException;
import java.net.URI;
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
   * @return the matching URI, or null if none matches
   *
   * @throws IOException if some low-level IO error has occurred
   */
  URI get(String encName, String scheme, long timeout) throws IOException;

  /**
   * Fetch all encoded names with the given suffix.
   */
  Set list(String encSuffix, long timeout) throws IOException;

}
