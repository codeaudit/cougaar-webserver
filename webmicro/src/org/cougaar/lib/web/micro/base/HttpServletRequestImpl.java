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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * An implementation of the http-servlet-request interface.
 * <p>
 * Note that some methods are not currently supported, e.g. sessions and
 * cookies.
 */
public class HttpServletRequestImpl implements HttpServletRequest {

  // from Tomcat HttpRequestBase:
  private static final String[] DATE_FORMATS = {
    "EEE, dd MMM yyyy HH:mm:ss zzz",
    "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
    "EEE MMMM d HH:mm:ss yyyy",
  };

  private final RequestCallback callback;

  private final String contextPath;

  private final String clientAddr;
  private final String clientHost;

  private String scheme;
  private String serverHost;
  private int serverPort;
  private String servletPath;

  private String method;
  private String requestURI;
  private String pathInfo;
  private String queryString;
  private Map parameters = new HashMap();

  private String protocol;

  private Map headers = new HashMap();

  private ServletInputStream inputStream;
  private boolean made_reader = false;

  public HttpServletRequestImpl(RequestCallback callback, Map metaData) {
    this.callback = callback;

    // save metaData
    this.contextPath = _get(metaData, "contextPath");
    this.clientAddr = _get(metaData, "clientAddr");
    this.clientHost = _get(metaData, "clientHost");
    parseServerURL(_get(metaData, "serverURL"));

    // read data
    parseRequest();
    parseHeaders();
    parseInputStream();
  }
  private static String _get(Map m, String key) {
    return (m == null ? null : (String) m.get(key));
  }

  // parse server base url, e.g.:
  //   http://bar.com:80/foo
  private void parseServerURL(String s) {
    try {
      String serverURL = s.trim();
      int scheme_sep = serverURL.indexOf("://");
      if (scheme_sep <= 0) {
        throw new IllegalArgumentException("Missing scheme");
      }
      scheme = serverURL.substring(0, scheme_sep);
      int name_start = scheme_sep + 3;
      int port_sep = serverURL.indexOf(':', name_start);
      int path_sep = serverURL.indexOf('/', name_start);
      if (path_sep < 0) {
        path_sep = serverURL.length();
      }
      if (port_sep > 0 && port_sep < path_sep) {
        serverHost = serverURL.substring(name_start, port_sep);
        serverPort = Integer.parseInt(serverURL.substring(port_sep+1, path_sep));
      } else {
        serverHost = serverURL.substring(name_start, path_sep);
        serverPort = 
          (scheme.equals("http") ? 80 :
           scheme.equals("https") ? 443 :
           -1);
      }
      if (serverHost.length() == 0) {
        throw new IllegalArgumentException("Missing host");
      }
      if (serverPort <= 0) {
        throw new IllegalArgumentException("Invalid port: "+serverPort);
      }
      servletPath = 
        (path_sep < serverURL.length() ? serverURL.substring(path_sep) : "/");
      if (servletPath.length() == 0 || servletPath.charAt(0) != '/') {
        throw new IllegalArgumentException("Invalid servletPath: "+servletPath);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid serverURL: "+s);
    }
  }

  // parse request line, e.g.:
  //   GET /foo/test?x=y HTTP/1.0
  private void parseRequest() {
    String request;
    try {
      request = callback.readRequest();
    } catch (IOException ioe) {
      throw new RuntimeException("Unable to read request line");
    }

    try {
      int begin = request.indexOf(' ', 1);
      int end = request.indexOf(' ', begin+1);
      method = request.substring(0, begin).trim();
      String uri = request.substring(begin+1, end).trim();
      protocol = request.substring(end+1).trim();

      // remove "http://blah:123/path" to be just "/path"
      requestURI = uri;
      int scheme_sep = uri.indexOf("://");
      if (scheme_sep > 0) {
        int slash_sep = uri.indexOf('/', scheme_sep+1);
        requestURI = (slash_sep > 0 ? uri.substring(slash_sep) : "/");
      }

      // remove excess "/"s in "///path" to be just "/path"
      for (int len = requestURI.length();
          len > 1 && requestURI.charAt(1) == '/';
          len--) {
        requestURI = requestURI.substring(1);
      }

      // extract and parse query parameters
      int query_sep = requestURI.indexOf('?');
      if (query_sep > 0) {
        queryString = requestURI.substring(query_sep+1);
        requestURI = requestURI.substring(0, query_sep);
        parseParameters(queryString);
      }

      // set our "pathInfo" subpath relative to the base path
      if (servletPath != null &&
          requestURI.startsWith(servletPath) &&
          requestURI.length() > servletPath.length()) {
        pathInfo = requestURI.substring(servletPath.length());
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid request line: "+request);
    }
  }

  // parse url/post parameters, e.g.:
  //   x=y&foo=bar
  private void parseParameters(String s) {
    String[] sa = s.split("&");
    for (int i = 0; i < sa.length; i++) {
      String sai = sa[i];
      int eq_sep = sai.indexOf('=');
      if (eq_sep <= 0) continue;
      String name = sai.substring(0, eq_sep);
      String value = sai.substring(eq_sep+1);
      Object o = parameters.get(name);
      if (o == null) {
        parameters.put(name, value);
      } else {
        if (!(o instanceof List)) {
          List l = new ArrayList();
          l.add(o);
          parameters.put(name, l);
          o = l;
        }
        ((List) o).add(value);
      }
    }
  }

  // parse headers, e.g.:
  //   bar: qux
  //   Content-Length: 1234
  private void parseHeaders() {
    List header_lines;
    try {
      header_lines = callback.readHeaders();
    } catch (IOException e) {
      throw new RuntimeException("Unable to read header lines");
    }

    for (int i = 0; i < header_lines.size(); i++) {
      String header = (String) header_lines.get(i);
      if (header.length() == 0) break;
      int sep = header.indexOf(':');
      if (sep < 0) continue;
      String name = header.substring(0, sep).trim();
      String value = header.substring(sep+1).trim();
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
  }

  // parse post-data input stream
  //
  // We must fully read the stream before we create our response stream,
  // That's why we create our "inputStream" here instead of waiting until
  // "getInputStream()/getReader()" is called.
  private void parseInputStream() {
    int contentLength = getContentLength();
    byte[] body;
    try {
      body = callback.readBody(contentLength);
    } catch (IOException ioe) {
      throw new RuntimeException("Unable to read body["+contentLength+"]");
    }

    if ("post".equalsIgnoreCase(method)) {
      // read posted parameters, reset body to zero
      parseParameters(new String(body));
      body = new byte[0];
    }

    final InputStream is = new ByteArrayInputStream(body);
    inputStream = new ServletInputStream() {
      public int read() throws IOException { return is.read(); }
      public int read(byte b[], int off, int len) throws IOException { return is.read(b, off, len); }
      public long skip(long n) throws IOException { return is.skip(n); }
      public int available() throws IOException { return is.available(); }
      public boolean markSupported() { return is.markSupported(); }
      public void mark(int readAheadLimit) { is.mark(readAheadLimit); }
      public void reset() throws IOException { is.reset(); }
      public void close() throws IOException { is.close(); }
    };
  }

  // server info
  public String getContextPath() { return contextPath; }
  public String getServerName() { return serverHost; }
  public int getServerPort() { return serverPort; }

  // client info
  public String getRemoteAddr() { return clientAddr; }
  public String getRemoteHost() { return clientHost; }

  // request info
  public boolean isSecure() { return "https".equals(getScheme()); }
  public String getScheme() { return scheme; }
  public String getMethod() { return method; }
  public String getPathInfo() { return pathInfo; }
  public String getServletPath() { return servletPath; }
  public String getQueryString() { return queryString; }
  public String getRequestURI() { return requestURI; }
  public String getProtocol() { return protocol; }
  public StringBuffer getRequestURL() {
    StringBuffer ret = new StringBuffer();
    ret.append(getScheme()).append("://").append(getServerName());
    int port = getServerPort();
    if (port < 0) {
      port = 80;
    }
    if ((scheme.equals("http") && (port != 80)) || 
        (scheme.equals("https") && (port != 443))) {
      ret.append(":").append(port);
    }
    ret.append(getRequestURI());
    return ret;
  }
  public String getParameter(String name) {
    Object o = parameters.get(name);
    return
      (o == null ? null :
       o instanceof String ? (String) o :
       (String) ((List) o).get(0));
  }
  public String[] getParameterValues(String name) {
    Object o = parameters.get(name);
    return
      (o == null ? null :
       o instanceof String ? new String[] { (String) o } :
       (String[]) ((List) o).toArray(new String[((List) o).size()]));
  }
  public Enumeration getParameterNames() {
    return Collections.enumeration(parameters.keySet());
  }
  public Map getParameterMap() {
    Map ret = new HashMap();
    for (Enumeration en = getParameterNames(); en.hasMoreElements(); ) {
      String s = (String) en.nextElement();
      ret.put(s, getParameterValues(s));
    }
    return ret;
  }

  // header info
  public String getHeader(String name) {
    Object o = headers.get(name);
    return
      (o == null ? null :
       o instanceof String ? (String) o :
       (String) ((List) o).get(0));
  }
  public Enumeration getHeaders(String name) {
    Object o = headers.get(name);
    List l = 
      (o == null ? Collections.EMPTY_LIST : 
       o instanceof String ? Collections.singletonList(o) :
       (List) o);
    return Collections.enumeration(l);
  }
  public Enumeration getHeaderNames() {
    return Collections.enumeration(headers.keySet());
  }
  public int getIntHeader(String name) {
    String s = getHeader(name);
    if (s == null) return -1;
    return Integer.parseInt(s);
  }
  public int getContentLength() {
    return getIntHeader("Content-Length");
  }
  public String getContentType() {
    return getHeader("Content-Type");
  }
  public long getDateHeader(String name) {
    String s = getHeader(name);
    if (s == null) return -1;
    for (int i = 0; i < DATE_FORMATS.length; i++) {
      DateFormat format = new SimpleDateFormat(DATE_FORMATS[i]);
      try {
        Date date = format.parse(s);
        return date.getTime();
      } catch (ParseException e) {
      }
    }
    throw new IllegalArgumentException(s);
  }

  // body info
  public ServletInputStream getInputStream() throws IOException {
    if (made_reader) {
      throw new IllegalStateException("Already made reader");
    }
    made_reader = true;
    return inputStream;
  }
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream()));
  }

  // unsupported:
  //   HttpServletRequest:
  public String getPathTranslated() { die(); return null; }
  public Cookie[] getCookies() { die(); return null; }
  public String getAuthType() { die(); return null; }
  public String getRemoteUser() { die(); return null; }
  public boolean isUserInRole(String role) { die(); return false; }
  public Principal getUserPrincipal() { die(); return null; }
  public String getRequestedSessionId() { die(); return null; }
  public HttpSession getSession(boolean create) { die(); return null; }
  public HttpSession getSession() { die(); return null; }
  public boolean isRequestedSessionIdValid() { die(); return false; }
  public boolean isRequestedSessionIdFromCookie() { die(); return false; }
  public boolean isRequestedSessionIdFromURL() { die(); return false; }
  public boolean isRequestedSessionIdFromUrl() { die(); return false; }
  //   ServletRequest:
  public String getCharacterEncoding() { die(); return null; }
  public void setCharacterEncoding(String env) throws UnsupportedEncodingException { die(); }
  public Object getAttribute(String name) { die(); return null; }
  public Enumeration getAttributeNames() { die(); return null; }
  public void setAttribute(String name, Object o) { die(); }
  public void removeAttribute(String name) { die(); }
  public Locale getLocale() { die(); return null; }
  public Enumeration getLocales() { die(); return null; }
  public RequestDispatcher getRequestDispatcher(String path) { die(); return null; }
  public String getRealPath(String path) { die(); return null; }
  private void die() {
    throw new UnsupportedOperationException();
  }
}


