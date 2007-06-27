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
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * An implementation of the http-servlet-response interface.
 * <p>
 * Note that some methods are not currently supported, e.g. cookies and
 * buffer resets.
 */
public class HttpServletResponseImpl implements HttpServletResponse {

  private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

  private final ResponseCallback callback;
  private boolean committed = false;
  private boolean made_writer = false;

  public HttpServletResponseImpl(ResponseCallback callback) {
    this.callback = callback;
  }

  public boolean isCommitted() {
    return committed;
  }

  // status code
  public void setStatus(int sc) {
    setStatus(sc, getStatusMessage(sc));
  }
  public void setStatus(int sc, String sm) {
    callback.setStatus(sc, sm);
  }
  public void sendError(int sc) throws IOException { 
    sendError(sc, getStatusMessage(sc));
  }
  public void sendError(int sc, String sm) throws IOException {
    callback.setError(sc, sm);
  }
  public void sendRedirect(String location) throws IOException {
    try {
      String absolute = location;  // convert to absolute url?
      setStatus(SC_MOVED_TEMPORARILY);
      setHeader("Location", absolute);
    } catch (IllegalArgumentException e) {
      setStatus(SC_NOT_FOUND);
    }
  }

  // encode
  public String encodeRedirectURL(String url) { return url; }
  public String encodeRedirectUrl(String url) { return encodeRedirectURL(url); }
  public String encodeURL(String url) { return url; }
  public String encodeUrl(String url) { return encodeURL(url); }

  // header info
  public void addHeader(String name, String value) {
    if (isCommitted()) return;
    Map headers = callback.getHeaders();
    Object o = headers.get(name);
    if (o == null) {
      headers.put(name, value);
    } else {
      if (!(o instanceof List)) {
        List l = new ArrayList();
        l.add(o);
        headers.put(name, l);
        o = l;
      }
      ((List) o).add(value);
    }
  }
  public void setHeader(String name, String value) {
    if (isCommitted()) return;
    Map headers = callback.getHeaders();
    headers.put(name, value);
  }
  public boolean containsHeader(String name) {
    Map headers = callback.getHeaders();
    return headers.containsKey(name);
  }
  public void setContentType(String type) {
    addHeader("Content-Type", type);
  }
  public void setContentLength(int len) {
    setIntHeader("Content-Length", len);
  }
  public void setDateHeader(String name, long date) {
    DateFormat format = new SimpleDateFormat(DATE_FORMAT);
    setHeader(name, format.format(new Date(date)));
  }
  public void addDateHeader(String name, long date) {
    DateFormat format = new SimpleDateFormat(DATE_FORMAT);
    addHeader(name, format.format(new Date(date)));
  }
  public void setIntHeader(String name, int value) {
    setHeader(name, Integer.toString(value));
  }
  public void addIntHeader(String name, int value) {
    addHeader(name, Integer.toString(value));
  }

  // stream
  public PrintWriter getWriter() throws IOException {
    if (made_writer) {
      throw new IllegalStateException("Already made writer");
    }
    made_writer = true;
    return callback.createWriter();
  }
  public ServletOutputStream getOutputStream() throws IOException {
    if (made_writer) {
      throw new IllegalStateException("Already made writer");
    }
    made_writer = true;
    return callback.createOutputStream();
  }

  private String getStatusMessage(int sc) {
    switch (sc) {
      case SC_OK: return "OK";
      case SC_MOVED_TEMPORARILY: return "Moved Temporarily";
      case SC_NOT_FOUND: return "Not Found";
      case SC_NOT_IMPLEMENTED: return "Not Implemented";
      case SC_NOT_MODIFIED: return "Not Modified";
      case SC_REQUEST_TIMEOUT: return "Request Timeout";
      case SC_SERVICE_UNAVAILABLE: return "Service Unavailable";
      default: return "HTTP Response Status " + sc;
    }
  }

  // unsupported:
  //   HttpServletResponse:
  public void addCookie(Cookie cookie) { die(); }
  //   ServletResponse:
  public String getCharacterEncoding() { die(); return null; }
  public void setBufferSize(int size) { die(); }
  public int getBufferSize() { die(); return -1; }
  public void flushBuffer() throws IOException { die(); }
  public void resetBuffer() { die(); }
  public void reset() { die(); }
  public void setLocale(Locale loc) { die(); }
  public Locale getLocale() { die(); return null; }
  private void die() {
    throw new UnsupportedOperationException();
  }
}
