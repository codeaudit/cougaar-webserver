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

package org.cougaar.lib.web.micro.http;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.lib.web.micro.base.ClientFactory;
import org.cougaar.lib.web.micro.base.Connection;
import org.cougaar.lib.web.micro.base.ServletTunnel;
import org.cougaar.lib.web.micro.base.SocketClientFactory;
import org.cougaar.lib.web.redirect.HttpServletRedirector;

/**
 * This component tunnels servlet requests through HTTP.
 */
public class HttpServletTunnel extends HttpServletRedirector {

  protected boolean isSupported(String s) {
    return ("http_tunnel".equals(s) || "-".equals(s));
  }

  protected void doRedirect(
      String encName,
      String location,
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    if (log.isInfoEnabled()) {
      log.info("Tunneling request for "+encName+" to "+location);
    }

    // encode for redirect -- typically a no-op
    String encLoc = res.encodeRedirectURL(location);

    // extract host:port
    URI uri;
    try {
      uri = URI.create(encLoc);
    } catch (Exception e) {
      throw new RuntimeException("Invalid tunnel location URI: "+encLoc);
    }

    // TODO use encLoc in ServletTunnel header line, in case the contextPath
    // is different on the remote host

    // tunnel
    Map metaData = ServletTunnel.extractMetaData(req);
    ClientFactory client_factory = new SocketClientFactory();
    Connection con = client_factory.connect(uri, metaData);
    ServletTunnel.tunnel(req, res, con);
  }

}
