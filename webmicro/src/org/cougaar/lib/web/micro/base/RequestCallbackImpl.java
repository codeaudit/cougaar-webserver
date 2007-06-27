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
import java.util.ArrayList;
import java.util.List;

/**
 * A standard request callback implementation that reads from an
 * {@link InputStream}.
 */
public class RequestCallbackImpl implements RequestCallback {

  private final InputStream is;

  public RequestCallbackImpl(InputStream is) {
    this.is = is;
  }

  public String readRequest() throws IOException {
    return readLine();
  }

  public List readHeaders() throws IOException {
    List ret = new ArrayList();
    while (true) {
      String s = readLine();
      if (s == null) break;
      if (s.length() == 0) break;
      ret.add(s);
    }
    return ret;
  }

  public byte[] readBody(int contentLength) throws IOException {
    int n = (contentLength > 0 ? contentLength : 0);
    byte[] body = new byte[n];
    for (int offset = 0; offset < n; ) {
      int count = is.read(body, offset, (n - offset));
      if (count < 0) break;
      offset += count;
    }
    return body;
  }

  private String readLine() throws IOException {
    StringBuffer buf = new StringBuffer();
    while (true) {
      int b = is.read();
      if (b < 0) break;
      if (b == '\r') b = is.read();
      if (b == '\n') break;
      buf.append((char) b);
    }
    return buf.toString().trim();
  }
}
