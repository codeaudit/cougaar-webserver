/*
 * <copyright>
 *  Copyright 2000-2001 BBNT Solutions, LLC
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

import java.io.*;
import java.net.*;
import java.util.*;

import org.cougaar.core.service.wp.*;

import org.cougaar.lib.web.arch.root.GlobalRegistry;

/**
 * Implementation of <code>GlobalRegistry</code> that uses
 * a JNDI naming service.
 *
 * @see GlobalRegistry
 */
public class NamingServerRegistry 
implements GlobalRegistry {

  private static final Application APP = 
    Application.getApplication("servlet");

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
    // check the url-encoded name
    if (encName == null) {
      throw new NullPointerException();
    }

    // register in white pages
    if (httpPort >= 0) {
      URI httpURI = URI.create(
          "http://"+
          httpAddr.getHostName()+
          ":"+
          httpPort+
          "/$"+
          encName);
      String rawName = URLDecoder.decode(encName);
      AddressEntry httpEntry =
        new AddressEntry(
            rawName,
            APP,
            httpURI,
            Cert.NULL,
            Long.MAX_VALUE);
      try {
        wp.rebind(httpEntry);
      } catch (Exception e) {
        throw new RuntimeException(
            "Unable to bind("+httpEntry+")", e);
      }
    }
    if (httpsPort > 0) {
      URI httpsURI = URI.create(
        "https://"+
        httpsAddr.getHostName()+
        ":"+
        httpsPort+
        "/$"+
        encName);
      String rawName = URLDecoder.decode(encName);
      AddressEntry httpsEntry =
        new AddressEntry(
            rawName,
            APP,
            httpsURI,
            Cert.NULL,
            Long.MAX_VALUE);
      try {
        wp.rebind(httpsEntry);
      } catch (Exception e) {
        throw new RuntimeException(
            "Unable to rebind("+httpsEntry+")", e);
      }
    }
  }

  public void unbind(String encName) throws IOException {
    // check the url-encoded name
    if (encName == null) {
      throw new NullPointerException();
    }

    // verify that "encName" was registered by this instance?

    // unregister from white pages
    if (httpPort > 0) {
      URI httpURI = URI.create("http://ignored");
      String rawName = URLDecoder.decode(encName);
      AddressEntry httpEntry =
        new AddressEntry(
            rawName,
            APP,
            httpURI,
            Cert.NULL,
            Long.MAX_VALUE);
      try {
        wp.unbind(httpEntry);
      } catch (Exception e) {
        throw new RuntimeException(
            "Unable to unbind("+httpEntry+")", e);
      }
    }
    if (httpsPort > 0) {
      URI httpsURI = URI.create("https://ignored");
      String rawName = URLDecoder.decode(encName);
      AddressEntry httpsEntry =
        new AddressEntry(
            rawName,
            APP,
            httpsURI,
            Cert.NULL,
            Long.MAX_VALUE);
      try {
        wp.unbind(httpsEntry);
      } catch (Exception e) {
        throw new RuntimeException(
            "Unable to unbind("+httpsEntry+")", e);
      }
    }
  }

  public URI get(final String encName, final String scheme) throws IOException {
    // check the url-encoded name
    if (encName == null || encName.length() == 0) {
      return null;
    }
    // check the scheme
    if (scheme == null || scheme.length() == 0) {
      return null;
    }

    String rawName = URLDecoder.decode(encName);
    AddressEntry[] a;
    try {
      a = wp.get(rawName);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to get("+rawName+")", e);
    }

    // extract matching app/scheme
    URI uri = null;
    for (int i = 0; i < a.length; i++) {
      AddressEntry ai = a[i];
      if (APP.equals(ai.getApplication())) {
        URI ui = ai.getAddress();
        String si = ui.getScheme();
        if (scheme.equals(si)) {
          uri = ui;
          break;
        }
      }
    }

    return uri;
  }

  public Set list(String encSuffix) throws IOException {
    String rawSuffix = URLDecoder.decode(encSuffix);
    Set s;
    try {
      s = wp.list(rawSuffix);
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
        String encName = URLEncoder.encode(rawName);
        ret.add(encName);
      }
    }
    return ret;
  }
}
