/*
 * <copyright>
 *  
 *  Copyright 2000-2004 BBNT Solutions, LLC
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.Callback;
import org.cougaar.core.service.wp.Response;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.lib.web.arch.root.GlobalRegistry;
import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;

/**
 * An implementation of {@link GlobalRegistry} that uses the
 * {@link WhitePagesService}.
 */
public class NamingServerRegistry implements GlobalRegistry {

  private static final Logger logger = Logging.getLogger(NamingServerRegistry.class);

  private final WhitePagesService wp;

  private Map namingEntries;

  public NamingServerRegistry(WhitePagesService wp) {
    this.wp = wp;
  }

  public void configure(Map namingEntries) {
    this.namingEntries = namingEntries;
  }

  public void rebind(String encName) {
    update(true, encName);
  }

  public void unbind(String encName) {
    update(false, encName);
  }

  private void update(boolean bind, String encName) {
    if (encName == null) {
      throw new NullPointerException();
    }

    if (namingEntries == null || namingEntries.isEmpty()) {
      return;
    }

    if (wp == null) {
      if (logger.isInfoEnabled()) {
        logger.info(
            "Ignoring servlet WP "+(bind ? "re" : "un")+"bind for "+encName);
      }
      return;
    }

    Callback callback = new Callback() {
      public void execute(Response res) {
        if (res.isSuccess()) {
          if (logger.isDebugEnabled()) {
            logger.debug("WP Response: "+res);
          }
        } else {
          logger.error("WP Error: "+res);
        }
      }
    };

    try {
      String rawName = decode(encName);
      for (Iterator iter = namingEntries.entrySet().iterator();
          iter.hasNext();
          ) {
        Map.Entry me = (Map.Entry) iter.next();
        String key = (String) me.getKey();
        URI value = (URI) me.getValue();
        AddressEntry entry = AddressEntry.getAddressEntry(rawName, key, value);

        if (bind) {
          wp.rebind(entry, callback);
        } else {
          wp.unbind(entry, callback);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(
        "Unable to "+(bind?"":"un")+"bind "+encName, e);
    }
  }

  public Map getAll(String encName, long timeout) {
    if (encName == null || encName.length() == 0) {
      return null;
    }

    if (wp == null) {
      if (logger.isInfoEnabled()) {
        logger.info("Ignoring servlet WP get for "+encName+")");
      }
      return null;
    }

    try {
      String rawName = decode(encName);
      return wp.getAll(rawName, timeout);
    } catch (Exception e) {
      throw new RuntimeException("Unable to getAll "+encName, e);
    }
  }

  public Set list(String encSuffix, long timeout) {
    String rawSuffix = decode(encSuffix);

    if (wp == null) {
      if (logger.isInfoEnabled()) {
        logger.info(
            "Ignoring servlet WP list("+
            rawSuffix+", "+timeout+")");
      }
      return Collections.EMPTY_SET;
    }

    Set s;
    try {
      s = wp.list(rawSuffix, timeout);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to list("+rawSuffix+")", e);
    }
    int n = s.size();
    Set ret;
    if (n <= 0) {
      ret = Collections.EMPTY_SET;
    } else {
      ret = new HashSet(n);
      Iterator iter = s.iterator();
      for (int i = 0; i < n; i++) {
        String rawName = (String) iter.next();
        String encName = encode(rawName);
        ret.add(encName);
      }
    }
    return ret;
  }

  private static final String encode(String raw) {
    try {
      return URLEncoder.encode(raw, "UTF-8");
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("Invalid name: "+raw, uee);
    }
  }

  private static final String decode(String enc) {
    try {
      return URLDecoder.decode(enc, "UTF-8");
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("Invalid name: "+enc, uee);
    }
  }
}
