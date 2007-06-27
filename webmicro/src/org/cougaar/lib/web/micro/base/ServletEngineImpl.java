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

package org.cougaar.lib.web.micro.base;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.util.Collections;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet engine backed by our {@link ServerFactory} support.
 */
public class ServletEngineImpl implements ServletEngine {

  private final ServerFactory factory;

  private Map settings;
  private boolean running;
  private ServerFactory.ListenerControl controller;
  private Servlet servlet;

  public ServletEngineImpl() {
    this(new SocketServerFactory());
  }
  public ServletEngineImpl(ServerFactory factory) {
    this.factory = factory;
    if (factory == null) {
      throw new IllegalArgumentException("null factory");
    }
  }

  /** A minimal command-line test. */
  public static void main(String[] args) throws Exception {
    int httpPort;
    try {
      httpPort = Integer.parseInt(args[0]);
    } catch (Exception e) {
      System.err.println("Usage: HTTP_PORT");
      return;
    }

    ServletEngine engine = new ServletEngineImpl();
    engine.configure(httpPort, -1, null);
    engine.start();
    engine.setGateway(new SnoopServlet());
  }

  //
  // ServletEngine API:
  //

  public void configure(int httpPort, int httpsPort, Map options) {
    this.settings = Collections.singletonMap("port", Integer.toString(httpPort));
  }
  public void start() throws BindException, IOException {
    running = true;

    ServerFactory.AcceptCallback callback = 
      new ServerFactory.AcceptCallback() {
        public void accept(Connection con) throws IOException {
          ServletEngineImpl.this.accept(con);
        }
      };
    controller = factory.listen(settings, callback);
  }
  public boolean isRunning() {
    return running;
  }
  public void setGateway(Servlet s) {
    this.servlet = s;
    if (s == null) return;
    // init servlet
    try {
      // TODO take name & params from options
      String name = "cougaar";
      Map params = Collections.EMPTY_MAP;
      ServletContext context = new ServletContextImpl();
      ServletConfig config = new ServletConfigImpl(name, context, params);
      s.init(config);
    } catch (Exception e) {
      throw new RuntimeException("Unable to init servlet", e);
    }
  }
  public void stop() {
    if (controller != null) {
      controller.stop();
      controller = null;
    }
  }

  //
  // impl:
  //

  private void accept(Connection con) throws IOException {
    try {
      // read request
      InputStream is = con.getInputStream();
      HttpServletRequest req =
        new HttpServletRequestImpl(
            new RequestCallbackImpl(is),
            con.getMetaData());

      // prepare response
      OutputStream out = con.getOutputStream();
      ResponseCallback rc = new ResponseCallbackImpl(out);
      HttpServletResponse res = new HttpServletResponseImpl(rc);

      // invoke servlet
      try {
        servlet.service(req, res);
      } catch (ServletException se) {
        throw new RuntimeException("Servlet threw exception", se);
      }

      // flush response
      rc.finishResponse();
    } finally {
      con.close();
    }
  }
}
