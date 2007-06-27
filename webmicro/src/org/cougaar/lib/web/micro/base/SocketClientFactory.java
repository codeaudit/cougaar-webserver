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
import java.io.Serializable;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;

/**
 * A standard {@link Socket}-based client connection factory implementation.
 */
public class SocketClientFactory implements ClientFactory {

  /**
   * @param o must be a URI
   */
  public Connection connect(Object o, Map metaData) throws IOException {
    if (!(o instanceof URI)) {
      throw new IllegalArgumentException(
          "Expecting a URI, not "+
          (o == null ? "null" : o.getClass().getName()));
    }
    URI uri = (URI) o;

    final Socket socket = new Socket(uri.getHost(), uri.getPort());

    // FIXME ignore metaData?  If we're sure we're connecting to our own
    // SocketServerFactory then we could send this data as a data header, but
    // if we're connecting to a standard HTTP listener then we have nowhere
    // to put this info...

    return new Connection() {
      public Map getMetaData() {
        return null; // not applicable
      }
      public AnnotatedInputStream getInputStream() throws IOException {
        return AnnotatedInputStream.toAnnotatedInputStream(
            socket.getInputStream());
      }
      public AnnotatedOutputStream getOutputStream() throws IOException {
        return AnnotatedOutputStream.toAnnotatedOutputStream(
            socket.getOutputStream());
      }
      public void close() throws IOException {
        socket.close();
      }
    };
  }

}
