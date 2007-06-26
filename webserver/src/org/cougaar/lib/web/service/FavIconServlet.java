/*
 * <copyright>
 *  
 *  Copyright 2000-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.core.servlet.ComponentServlet;
import org.cougaar.util.ConfigFinder;

/**
 * This servlet handles "/favicon.ico" requests with our local "favicon.ico"
 * image file.
 * <p>
 * We first check our config path for the file, then the jar resources.
 * <p>
 * Browsers ask for this image and display it on the URL line as a tiny icon.
 * Unless we load this servlet, Mozilla will ask for this URL after every
 * request.
 *
 * @property org.cougaar.lib.web.service.favIcon_ttd=360000
 *   How long a browser should cache the "/favicon.ico" image, defaults to one
 *   hour in milliseconds
 */
public class FavIconServlet extends ComponentServlet {

  // default to one hour
  private long expireMillis = 
    SystemProperties.getLong(
        "org.cougaar.lib.web.service.favIcon_ttd", 60*60*1000);

  public void setParameter(Object o) {
    super.setParameter(o);
    if (!(o instanceof List)) return;
    List l = (List) o;
    if (l.size() < 2) return;
    String s = (String) l.get(1);
    expireMillis = Long.parseLong(s);
  }

  protected String getPath() {
    String ret = super.getPath();
    return (ret == null ? "/favicon.ico" : ret);
  }

  public void doGet(
      HttpServletRequest req, HttpServletResponse res
      ) throws ServletException, IOException {

    // set "Expires" header to force browser-side caching
    String expireDate = null;
    if (expireMillis > 0) {
      try {
        long now = System.currentTimeMillis();
        long expireTime = now + expireMillis;
        String format = "EEE, dd MMM yyyy HH:mm:ss zzz";
        DateFormat formatter = new SimpleDateFormat(format);
        expireDate = formatter.format(new Date(expireTime));
      } catch (Exception e) {
        // ignore, shouldn't happen
      }
    }
    if (expireDate != null) {
      res.setHeader("Expires", expireDate);
    }

    // read icon data to buffer
    ByteArrayOutputStream baos;
    try {
      InputStream in;
      try {
        // try config finder
        in = ConfigFinder.getInstance().open("favicon.ico");
      } catch (Exception e) {
        // else try jar
        in = getClass().getResourceAsStream("favicon.ico");
      }
      baos = new ByteArrayOutputStream(4096);
      byte[] buf = new byte[4096];
      while (true) {
        int count = in.read(buf);
        if (count < 0) break;
        baos.write(buf, 0, count);
      }
    } catch (Exception e2) {
      // not found
      baos = null;
    }

    // write buffered icon data to browser
    int length = (baos == null ? 0 : baos.size());
    if (length <= 0) {
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    res.setContentType("image/x-icon");
    res.setContentLength(length);
    OutputStream out = res.getOutputStream();
    baos.writeTo(out);
    out.close();
  }
}
