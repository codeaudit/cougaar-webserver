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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * A utility class to tunnel servlet requests through a client
 * {@link Connection}.
 * <p>
 * Typical usage is:<pre>
 *   Map metaData = {@link ServletTunnel#extractMetaData}(req);
 *   Connection con = {@link ClientFactory#connect}(uri, metaData);
 *   {@link ServletTunnel#tunnel}(req, res, con);
 * </pre>
 */
public final class ServletTunnel {

  private ServletTunnel() {}

  /**
   * Extract the non-header metadata from the servlet request.
   * <p>
   * We use this to tunnel this information to the remote server, otherwise
   * it won't know our client's IP address and other non-header info.
   */
  public static Map extractMetaData(HttpServletRequest req) {
    Map ret = new HashMap();
    ret.put(
        "serverURL", 
        req.getScheme()+"://"+req.getServerName()+":"+req.getServerPort()+
        req.getServletPath());
    ret.put("contextPath", req.getContextPath());
    ret.put("clientAddr",  req.getRemoteAddr());
    ret.put("clientHost",  req.getRemoteHost());
    ret = Collections.unmodifiableMap(ret);
    return ret;
  }

  /**
   * Tunnel a servlet call through a remote connection.
   */
  public static void tunnel(
      HttpServletRequest req, HttpServletResponse res,
      Connection con
      ) throws ServletException, IOException {

    // forward header and post data
    AnnotatedOutputStream out = con.getOutputStream();
    {
      boolean is_post = "post".equalsIgnoreCase(req.getMethod());

      boolean is_http11 = "HTTP/1.1".equalsIgnoreCase(req.getProtocol());

      // write request line
      //
      // FIXME we force HTTP/1.0, since it's awkward to handle chunked data.
      // For future reference, Tomcat's chunk-parser is in:
      //   org.apache.catalina.connector.http.HttpRequestStream
      // The major problem of switching to HTTP/1.0 is that we'll mangle any
      // chunked content data, as noted below.
      String queryString = req.getQueryString();
      out.print(req.getMethod()+" "+req.getRequestURI());
      if (queryString != null) {
        out.print("?"+queryString);
      }
      out.println(" HTTP/1.0");

      // get posted parameters "name=value" line
      String post_params = null;
      if (is_post) {
        StringBuffer buf = new StringBuffer();
        Map m = req.getParameterMap();
        for (Iterator iter = m.entrySet().iterator(); iter.hasNext(); ) {
          Map.Entry me = (Map.Entry) iter.next();
          String name = (String) me.getKey();
          String[] values = (String[]) me.getValue();
          for (int i = 0; i < values.length; i++) {
            if (buf.length() > 0) {
              buf.append("&");
            }
            buf.append(name).append("=").append(values[i]);
          }
        }
        post_params = buf.toString();
      }

      // write headers:
      if (is_post) {
        out.println("Content-Length: "+post_params.length());
      }
      boolean chunked = false;
      for (Enumeration en = req.getHeaderNames(); en.hasMoreElements(); ) {
        String name = (String) en.nextElement();
        if (is_post && "Content-Length".equalsIgnoreCase(name)) {
          continue;
        }
        for (Enumeration e2 = req.getHeaders(name); e2.hasMoreElements(); ) {
          String value = (String) e2.nextElement();
          if ("Transfer-Encoding".equalsIgnoreCase(name)) {
            chunked = "chunked".equalsIgnoreCase(value);
          }
          out.println(name+": "+value);
        }
      }
      out.println();

      // write post data:
      if (is_post) {
        out.print(post_params);
      } else {
        int r_contentLength = req.getContentLength();
        InputStream r_in = req.getInputStream();
        if (r_contentLength >= 0) {
          // FIXME if (chunked && is_http11) then we should either:
          //   a) "unchunk" the stream
          // or
          //   b) use HTTP/1.1 and parse the chunks, including the "0"-length
          //      end chunk marker.
          pipeTo(r_in, out, r_contentLength, chunked);
        } else {
          // FIXME according to the HTTP spec this seems like an error, but we
          // may be missing some valid cases (e.g. chunked transfer data)
        }
      }

      // send data
      out.flush();

      // we're done writing & flushing, but we can't close the stream yet,
      // otherwise a socket-based connection will complain.
      out.done();
    }

    // read from our pipe
    InputStream in = con.getInputStream();

    // read status, e.g.:
    //   HTTP/1.0 200 OK 
    String status = readLine(in);
    if (status == null) {
      throw new RuntimeException("Missing status");
    }

    // read headers
    String location = null;
    int contentLength = -1;
    boolean chunked = false;
    while (true) {
      String s = readLine(in);
      if (s == null) break;
      if (s.length() == 0) break;
      int sep = s.indexOf(':');
      if (sep <= 0) {
        throw new RuntimeException("Invalid header: "+s);
      }
      String name = s.substring(0, sep).trim();
      String value = s.substring(sep+1).trim();
      res.addHeader(name, value);
      if ("Content-Length".equalsIgnoreCase(name)) {
        contentLength = Integer.parseInt(value);
        res.setContentLength(contentLength);
      } else if ("Content-Type".equalsIgnoreCase(name)) {
        res.setContentType(value);
      } else if ("Location".equalsIgnoreCase(name)) {
        location = value;
      } else if ("Transfer-Encoding".equalsIgnoreCase(name)) {
        chunked = "chunked".equalsIgnoreCase(value);
      }
    }

    // set status
    {
      int sc_sep = status.indexOf(' ');
      int sm_sep = status.indexOf(' ', sc_sep+1);
      if (sm_sep < 0) {
        sm_sep = status.length();
      }
      int sc = Integer.parseInt(status.substring(sc_sep+1, sm_sep).trim());
      if (sc < 300 || sc == HttpServletResponse.SC_NOT_MODIFIED) {
        // okay
        res.setStatus(sc);
      } else if (sc < 400) {
        // redirect?  Must read "Location" header.
        if (location == null) {
          throw new RuntimeException(
              "Expecting a \"Location\" header for \""+status+"\"");
        }
        res.sendRedirect(location);
      } else {
        // error
        //
        // use "setStatus" instead of "sendError" -- see bug 1259
        //String sm = status.substring(sm_sep+1).trim();
        res.setStatus(sc);
      }
    }

    // read data
    if (location == null) {
      ServletOutputStream r_out = res.getOutputStream();
      pipeTo(in, r_out, contentLength, chunked);
    }

    // done
    in.close();
    out.close();
    con.close();
  }

  private static void pipeTo(
      final InputStream is, OutputStream out,
      int contentLength, boolean chunked
      ) throws IOException {
    // we use an "annotated" stream to preserve the "out.flush()" requests.
    AnnotatedInputStream ais = AnnotatedInputStream.toAnnotatedInputStream(is);
    if (contentLength >= 0) {
      byte[] buf = new byte[Math.max(2048, contentLength)];
      for (int i = 0; i < contentLength; ) {
        int count = ais.read2(buf, 0, Math.max(buf.length, (contentLength - i)));
        if (count == AnnotatedInputStream.NOOP) {
          continue;
        }
        if (count == AnnotatedInputStream.FLUSH) {
          out.flush();
          continue;
        }
        if (count < 0) break;
        out.write(buf, 0, count);
        i += count;
      }
    } else {
      byte[] buf = new byte[2048];
      while (true) {
        int count = ais.read2(buf);
        if (count == AnnotatedInputStream.NOOP) {
          continue;
        }
        if (count == AnnotatedInputStream.FLUSH) {
          out.flush();
          continue;
        }
        if (count < 0) break;
        out.write(buf, 0, count);
      }
    }
  }

  private static String readLine(InputStream in) throws IOException {
    StringBuffer buf = new StringBuffer();
    while (true) {
      int b = in.read();
      if (b < 0) break;
      if (b == '\r') b = in.read();
      if (b == '\n') break;
      buf.append((char) b);
    }
    return buf.toString().trim();
  }
}
