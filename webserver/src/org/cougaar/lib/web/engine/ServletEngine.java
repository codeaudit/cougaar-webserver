/*
 * <copyright>
 *  
 *  Copyright 1997-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.engine;

import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

/**
 * A servlet engine, as used by the {@link ServletEngineRegistryService}.
 *
 * @see ServletEngineService
 */
public interface ServletEngine {

  /**
   * Set the single "gateway" servlet that will handle all client requests.
   */
  void setGateway(Servlet s) throws ServletException;

  /**
   * Get the naming service URIs for this servlet engine.
   * <p>
   * For example:<pre>
   *    "http"  -&gt; "http://myhost:80/"
   *    "https" -&gt; "http://myhost:443/cougaar"
   * </pre>
   *
   * @return a Map of String to {@link java.net.URI}s that should be advertised
   *   in the naming service.  This map will be advertised for every local
   *   agent.
   */
  Map getNamingEntries();

}
