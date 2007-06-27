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

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * A standard {@link ServerSocket}-based server connection factory implementation.
 * <p>
 * This implementation supports subclassing to use pooled threads.
 */
public class SocketServerFactory implements ServerFactory {

  public ListenerControl listen(
      final Map settings, final AcceptCallback callback) throws BindException, IOException {

    ServerSocket serverSock = bind(settings);

    listen_bg(serverSock, settings, callback);

    // TODO return object with "stop()" method, to release thread & close socket
    return null;
  }

  /** bind a socket */
  protected ServerSocket bind(Map settings) throws BindException, IOException {
    int port = Integer.parseInt((String) settings.get("port"));
    return new ServerSocket(port);
  }

  /** listen in a background thread */
  protected void listen_bg(
      final ServerSocket serverSock,
      final Map settings,
      final AcceptCallback callback) {
    // TODO use pooled thread (but note that we'll never return this thread!)
    Runnable r = new Runnable() {
      public void run() {
        listen(serverSock, settings, callback);
      }
    };
    Thread thread = new Thread(r, "servlet engine");
    //thread.setDaemon(true);
    thread.start();
  }

  /** block in a loop, accepting clients */
  protected void listen(
      ServerSocket serverSock,
      Map settings,
      AcceptCallback callback) {
    int port = Integer.parseInt((String) settings.get("port"));
    String contextPath = "";
    String serverURL = "http://localhost:"+port;

    while (true) {
      final Socket clientSock;
      try {
        clientSock = serverSock.accept();
      } catch (Exception e) {
        throw new RuntimeException("Socket accept failed", e);
      }

      final Map metaData = new HashMap();
      metaData.put("serverURL", serverURL);
      metaData.put("contextPath", contextPath);
      InetAddress clientAddr = clientSock.getInetAddress();
      if (clientAddr != null) {
        metaData.put("clientAddr", clientAddr.getHostAddress());
        metaData.put("clientHost", clientAddr.getHostName());
      }

      Connection con = new Connection() {
        private String to_string;
        public Map getMetaData() {
          return metaData;
        }
        public AnnotatedInputStream getInputStream() throws IOException {
          return AnnotatedInputStream.toAnnotatedInputStream(
              clientSock.getInputStream());
        }
        public AnnotatedOutputStream getOutputStream() throws IOException {
          return AnnotatedOutputStream.toAnnotatedOutputStream(
              clientSock.getOutputStream());
        }
        public void close() throws IOException {
          clientSock.close();
        }
        public String toString() {
          if (to_string == null) {
            String s = null;
            try {
              s = clientSock.toString();
            } catch (Exception e) {
            }
            to_string = (s == null ? "null" : s);
          }
          return to_string;
        }
      };

      accept_bg(callback, con);
    }
  }

  /** call "callback.accept(con)" in a background thread */
  protected void accept_bg(
      final AcceptCallback callback,
      final Connection con) {
    // TODO use pooled thread
    Runnable r = new Runnable() {
      public void run() {
        accept(callback, con);
      }
    };
    (new Thread(r)).start();
  }

  /** call "callback.accept(con)" in the caller's thread */
  protected void accept(AcceptCallback callback, Connection con) {
    try {
      callback.accept(con);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
