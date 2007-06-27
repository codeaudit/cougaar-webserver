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
import java.net.BindException;
import java.net.ServerSocket;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.cougaar.core.service.ThreadService;
import org.cougaar.core.thread.Schedulable;
import org.cougaar.lib.web.engine.AbstractServletEngine;
import org.cougaar.lib.web.micro.base.Connection;
import org.cougaar.lib.web.micro.base.ServerFactory;
import org.cougaar.lib.web.micro.base.ServletEngine;
import org.cougaar.lib.web.micro.base.ServletEngineImpl;
import org.cougaar.lib.web.micro.base.SocketServerFactory;

/**
 * This component creates a minimal servlet engine based on the trimmed-down
 * {@link ServletEngineImpl} implementation.
 * <p>
 * This implementation is intended as a drop-in replacement for the 
 * heavier-weight, Tomcat-based "webtomcat" servlet engine.
 */
public class HttpServletEngine extends AbstractServletEngine {

  private boolean made_engine = false;
  private ThreadService threadService;
  private ServletEngine engine;

  public void unload() {
    releaseEngine();
    super.unload();
  }

  // forward all calls to our engine
  protected void configure(int httpPort, int httpsPort, Map options) {
    findOrMakeEngine().configure(httpPort, httpsPort, options);
  }
  protected void startServer() throws BindException, IOException {
    findOrMakeEngine().start();
  }
  protected void setGateway(Servlet s) throws ServletException {
    findOrMakeEngine().setGateway(s);
  }
  protected void stopServer() {
    findOrMakeEngine().stop();
  }

  // create the engine on first request
  private ServletEngine findOrMakeEngine() {
    if (made_engine) {
      return engine;
    }
    made_engine = true;

    // get thread service
    threadService = (ThreadService)
      sb.getService(this, ThreadService.class, null);
    if (threadService == null) {
      throw new RuntimeException("Unable to obtains ThreadService");
    }

    // subclass factory to use threading service
    ServerFactory server_factory = 
      new SocketServerFactory() {
        protected void listen_bg(
            final ServerSocket serverSock,
            final Map settings,
            final AcceptCallback callback) {
          Runnable r = new Runnable() {
            public void run() {
              if (log.isDebugEnabled()) {
                log.debug("listen");
              }
              listen(serverSock, settings, callback);
            }
          };
          // we'll block forever, but it's still better to use a pooled thread
          // than to spawn a raw Java thread
          Schedulable thread = threadService.getThread(
              this, r, "HttpServletEngine listener "+serverSock,
              ThreadService.WILL_BLOCK_LANE);
          thread.start();
        }
        protected void accept_bg(
            final AcceptCallback callback,
            final Connection con) {
          Runnable r = new Runnable() {
            public void run() {
              if (log.isDebugEnabled()) {
                log.debug("accept "+con);
              }
              accept(callback, con);
            }
          };
          // this will block if the servlet keeps the stream open
          Schedulable thread = threadService.getThread(
              this, r, "HttpServletEngine accept "+con,
              ThreadService.WILL_BLOCK_LANE);
          thread.start();
        }
      };

    // create base engine
    engine = new ServletEngineImpl(server_factory);

    return engine;
  }

  private void releaseEngine() {
    if (!made_engine) {
      return;
    }
    made_engine = false;

    // release engine
    if (engine != null) {
      if (engine.isRunning()) {
        engine.stop();
      }
      engine = null;
    }

    // release services
    if (threadService != null) {
      sb.releaseService(this, ThreadService.class, threadService);
      threadService = null;
    }
  }
}
