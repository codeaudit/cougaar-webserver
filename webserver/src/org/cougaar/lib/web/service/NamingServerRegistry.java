/*
 * <copyright>
 *  Copyright 2000-2003 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
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
import java.util.Set;
import org.cougaar.util.log.Logging;
import org.cougaar.util.log.Logger;
import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.Callback;
import org.cougaar.core.service.wp.Response;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.lib.web.arch.root.GlobalRegistry;

/**
 * Implementation of <code>GlobalRegistry</code> that uses
 * a JNDI naming service.
 *
 * @see GlobalRegistry
 */
public class NamingServerRegistry 
implements GlobalRegistry {

  private static final Logger logger = Logging.getLogger(NamingServerRegistry.class);

  private final WhitePagesService wp;

  private InetAddress httpAddr;
  private InetAddress httpsAddr;
  private int httpPort = -1;
  private int httpsPort = -1;

  public NamingServerRegistry(WhitePagesService wp) {
    this.wp = wp;
  }

  public void configure(
      int httpPort,
      int httpsPort) throws IOException {
    InetAddress localAddr = InetAddress.getLocalHost();
    this.httpAddr = localAddr;
    this.httpPort = httpPort;
    this.httpsAddr = localAddr;
    this.httpsPort = httpsPort;
  }

  public void rebind(String encName) throws IOException {
    update(true, encName, "http",  httpAddr,  httpPort);
    update(true, encName, "https", httpsAddr, httpsPort);
  }

  public void unbind(String encName) throws IOException {
    update(false, encName, "http",  httpAddr,  httpPort);
    update(false, encName, "https", httpsAddr, httpsPort);
  }

  private void update(
      boolean bind,
      String encName,
      String scheme,
      InetAddress addr,
      int port) {
    if (port < 0) {
      return;
    }

    if (encName == null) {
      throw new NullPointerException();
    }

    // register in white pages
    URI uri = URI.create(
      scheme+
      "://"+
      addr.getHostName()+
      ":"+
      port+
      "/$"+
      encName);
    String rawName = decode(encName);
    AddressEntry entry =
      AddressEntry.getAddressEntry(
        rawName, scheme, uri);

    Callback callback = new Callback() {
      public void execute(Response res) {
        if (res.isSuccess()) {
          if (logger.isInfoEnabled()) {
            logger.info("WP Response: "+res);
          }
        } else {
          logger.error("WP Error: "+res);
        }
      }
    };

    try {
      if (bind) {
        wp.rebind(entry, callback);
      } else {
        wp.unbind(entry, callback);
      }
    } catch (Exception e) {
      throw new RuntimeException(
        "Unable to "+(bind?"":"un")+"bind("+entry+")", e);
    }
  }

  public URI get(
      final String encName,
      final String scheme,
      final long timeout) throws IOException {
    // check the url-encoded name
    if (encName == null || encName.length() == 0) {
      return null;
    }
    // check the scheme
    if (scheme == null || scheme.length() == 0) {
      return null;
    }

    String rawName = decode(encName);
    AddressEntry ae;
    try {
      ae = wp.get(rawName, scheme, timeout);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to get("+rawName+")", e);
    }
    URI uri = (ae == null ? null : ae.getURI());

    return uri;
  }

  public Set list(
      String encSuffix,
      long timeout) throws IOException {
    String rawSuffix = decode(encSuffix);
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
