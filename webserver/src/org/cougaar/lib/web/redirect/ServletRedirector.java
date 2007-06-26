/*
 * <copyright>
 *  
 *  Copyright 2000-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.redirect;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A redirector / tunneler for servlet requests.
 */
public interface ServletRedirector {

  /**
   * The request has been redirected or otherwise committed.
   * <p>
   * This is the "success" case, even if it calls "res.sendError(..)".
   */
  int REDIRECTED = 0;

  /**
   * The specified options are not supported by this redirector.
   * <p>
   * This case is common if explicit options are specified.  Typically a
   * null options list is treated as a wildcard and always supported.
   */
  int NOT_SUPPORTED = 1;

  /**
   * The naming entries for the target do not contain a usable entry for
   * this redirector.
   * <p>
   * For example, an HTTP redirector may find that there's no "http" entry
   * in the naming service for the remote agent.
   */
  int NO_NAMING_ENTRIES = 2;

  /**
   * A redirection loop has been detected.
   * <p>
   * For example, an HTTP redirect might find its own host:port for a "remote"
   * agent, yet if that agent was local then we wouldn't have called the
   * redirector.
   */
  int DETECTED_LOOP = 3;

  /**
   * Some other, non-fatal error.
   * <p>
   * For example, the naming entry may be invalid.  This is in contrast to a
   * committed error case, in which case our redirector should throw an
   * exception or call "res.sendError(..)" and return {@link #REDIRECTED}.
   */
  int OTHER_ERROR = 4;

  /**
   * Redirect/Tunnel a servlet request to the specified remote agent.
   * <p>
   * A standard solution is to lookup the request's scheme in the map and
   * HTTP-redirect the browser to that remote host:port.  Another option is
   * to tunnel the request for the client.
   *
   * @param encName URL-encoded remote agent name.
   *
   * @param options URL-decoded redirect control options, as specified on
   *   the request path.  For example, "/$_^foo" is parsed as ["_", "^"].
   *   The meaning of these control options is redirector-specific.  Typically
   *   these options are only specified to override the default behavior, e.g.
   *   to turn off HTTP-redirects or use a custom redirect/tunnel capability.
   *
   * @param namingSupport support API to lookup the encName entries in the
   *   naming service.  See {@link ServletEngineService#getNamingEntries}.
   *
   * @param req the http request
   * @param res the http response
   *
   * @return {@link #REDIRECTED} if the call was sucessful or otherwise
   *   committed, otherwise one of the other status codes.
   */
  int redirect(
      String encName,
      List options,
      NamingSupport namingSupport,
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException;

}
