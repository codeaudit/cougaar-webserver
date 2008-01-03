/*
 * <copyright>
 *  
 *  Copyright 2000-2008 BBNT Solutions, LLC
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

package org.cougaar.lib.web.redirect;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.lib.web.arch.root.GlobalRegistry;
import org.cougaar.lib.web.arch.root.Redirector;

/**
 * A {@link Redirector} wrapper that checks the status code and, if not
 * successfully redirected, writes an error page.
 */
public class RedirectorWrapper implements Redirector {

  private final ServletRedirector redirector;
  private final GlobalRegistry globReg;

  public RedirectorWrapper(
      ServletRedirector redirector, GlobalRegistry globReg) {
    this.redirector = redirector;
    this.globReg = globReg;
  }

  public void redirect(
      String encName,
      List options,
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    int status;
    Exception error = null;
    NamingSupportImpl namingSupport = null;

    if (redirector == null) {
      // no redirector
      status = ServletRedirector.NOT_SUPPORTED;
    } else {
      // create naming wrapper
      namingSupport = new NamingSupportImpl();

      // attempt redirect
      try {
        status = redirector.redirect(encName, options, namingSupport, req, res);
      } catch (Exception e) {
        status = ServletRedirector.OTHER_ERROR;
        error = e;
        e.printStackTrace();
      }

      if (status == ServletRedirector.REDIRECTED) {
        // success or redirector-custom error page
        return;
      }
    }

    // write error page
    int errorCode;
    String header;
    String message;
    switch (status) {
      case ServletRedirector.NOT_SUPPORTED:
        errorCode = HttpServletResponse.SC_NOT_IMPLEMENTED;
        header = "unsupported_redirect";
        message = "Unsupported redirect options: "+options;
        break;
      case ServletRedirector.NO_NAMING_ENTRIES:
        errorCode = HttpServletResponse.SC_NOT_FOUND;
        header = "agent";
        message = ("\""+encName+"\" Not Found");
        break;
      case ServletRedirector.DETECTED_LOOP:
        errorCode = HttpServletResponse.SC_NOT_FOUND;
        header = "stale_naming";
        message = 
          "Detected stale naming entries that would have resulted in a"+
          " redirect loop: "+namingSupport.getNamingEntries(encName, -1);
        break;
      default:
        errorCode = HttpServletResponse.SC_NOT_FOUND;
        header = "other_error";
        message = 
          (error == null ? "Unspecified redirect error" :
           "Redirect exception: "+error);
        break;
    }
    res.setContentType("text/plain");
    res.setStatus(errorCode);
    res.addHeader("Cougaar-error", header);
    PrintWriter out = res.getWriter();
    out.println(message);
  }

  private class NamingSupportImpl implements NamingSupport {

    private final Map cache = new HashMap();

    public Map getNamingEntries(String encName, long timeout) {
      // we keep a cache so we don't block for the same timeout for every
      // redirector attempt, plus to make sure that all our redirectors see
      // the same naming data snapshots.
      synchronized (cache) {
        // check cache
        Object o = cache.get(encName);
        if (o instanceof Map) {
          // found in cached
          return (Map) o;
        }
        if (o instanceof Long) {
          // check to see if our cached timeout is longer than the new one
          long t = ((Long) o).longValue();
          if (t < 0) {
            if (timeout < 0) {
              return null;
            }
          } else if (t == 0) {
            return null;
          } else {
            if (timeout < 0 || (timeout > 0 && timeout <= t)) {
              return null;
            }
          }
        }

        // do lookup
        Map m;
        try {
          m = globReg.getAll(encName, timeout);
        } catch (Exception e) {
          // either timeout or real exception
          m = null;
        }

        // convert from name->entry(type,uri) to type->uri
        if (m != null && !m.isEmpty()) {
          Map m2 = new HashMap(m.size());
          for (Iterator iter = m.values().iterator(); iter.hasNext(); ) {
            AddressEntry ae = (AddressEntry) iter.next();
            m2.put(ae.getType(), ae.getURI());
          }
          m = Collections.unmodifiableMap(m2);
        }

        // cache result
        o = m;
        if (o == null) {
          o = new Long(timeout);
        }
        cache.put(encName, o);

        // return possibly null map
        return m;
      }
    }
  }
}
