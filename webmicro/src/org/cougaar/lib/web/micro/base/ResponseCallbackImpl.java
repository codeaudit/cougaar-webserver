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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;

/**
 * A standard implementation of the {@link ResponseCallback} API.
 */
public class ResponseCallbackImpl implements ResponseCallback {

  private final String protocol = "HTTP/1.0";
  private final Map headers = new HashMap();
  private int status = 200;
  private String message = "OK";
  private boolean error = false;

  private final OutputStream os;
  private ServletOutputStream out;
  private PrintWriter writer;

  public ResponseCallbackImpl(OutputStream os) {
    this.os = os;
  }

  public boolean isError() { return error; }
  public void setStatus(int status, String message) {
    this.status = status;
    this.message = message;
    this.error = false;
  }
  public void setError(int status, String message) {
    this.status = status;
    this.message = message;
    this.error = true;
  }
  public String getStatusLine() {
    return
      protocol+" "+status+
      (message == null ? "" : (" "+message))+
      "\r\n";
  }

  public Map getHeaders() { return headers; }
  public String getHeaderLines() {
    StringBuffer buf = new StringBuffer();
    for (Iterator iter = headers.entrySet().iterator();
        iter.hasNext();
        ) {
      Map.Entry me = (Map.Entry) iter.next();
      String name = (String) me.getKey();
      Object o = me.getValue();
      if (o instanceof String) {
        buf.append(name).append(": ").append(o).append("\r\n");
      } else {
        List l = (List) o;
        for (int i = 0; i < l.size(); i++) {
          buf.append(name).append(": ").append(l.get(i)).append("\r\n");
        }
      }
    }
    buf.append("\r\n");
    return buf.toString();
  }

  public ServletOutputStream createOutputStream() throws IOException {
    if (out != null || writer != null) {
      throw new IllegalStateException(
          "Already created "+(writer == null ? "OutputStream" : "Writer"));
    }
    if (os instanceof ServletOutputStream) {
      out = (ServletOutputStream) os;
    } else {
      out = new ServletOutputStream() {
        public void write(int b) throws IOException { os.write(b); }
        public void write(byte b[]) throws IOException { os.write(b); }
        public void write(byte b[], int off, int len) throws IOException {
          os.write(b, off, len);
        }
        public void flush() throws IOException { os.flush(); }
        public void close() throws IOException { os.close(); }
      };
    }
    sendHeaders();
    return out;
  }

  public PrintWriter createWriter() throws IOException {
    if (out != null || writer != null) {
      throw new IllegalStateException(
          "Already created "+(writer == null ? "OutputStream" : "Writer"));
    }
    writer = new PrintWriter(new OutputStreamWriter(os));
    sendHeaders();
    return writer;
  }

  private void sendHeaders() throws IOException {
    if (out != null) {
      out.print(getStatusLine());
      out.print(getHeaderLines());
      out.flush();
    } else if (writer != null) {
      writer.print(getStatusLine());
      writer.print(getHeaderLines());
      writer.flush();
    } else {
      throw new IllegalStateException("Missing stream");
    }
  }

  public void finishResponse() throws IOException {
    // close streams, let the "close()" do the flush.
    if (out == null && writer == null) {
      // create stream to write our headers
      out = createOutputStream(); 
      out.close();
    } else if (out != null) {
      out.close();
    } else {
      writer.close();
    }
  }
}
