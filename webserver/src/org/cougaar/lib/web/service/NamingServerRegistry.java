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
import javax.naming.*;
import javax.naming.directory.*;

import org.cougaar.lib.web.arch.root.GlobalEntry;
import org.cougaar.lib.web.arch.root.GlobalRegistry;

/**
 * Implementation of <code>GlobalRegistry</code> that uses
 * a JNDI naming service.
 *
 * @see GlobalRegistry
 */
public class NamingServerRegistry 
implements GlobalRegistry {

  /**
   * Directory in the naming service.
   */
  private static final String DIRNAME = "WEBSERVERS";

  private static final String GLOBAL_ENTRY_CLASSNAME =
        GlobalEntry.class.getName();

  /**
   * The provided directory context for access to the
   * naming service.
   */
  private final DirContext ctx;

  /**
   * A template <code>GlobalEntry</code> that represents
   * this registry instance's configured HTTP/HTTPS
   * location.
   */
  private GlobalEntry localTemplate;

  public NamingServerRegistry(DirContext root) throws NamingException {
    // create an entry
    DirContext d;
    try {
      d = root.createSubcontext(DIRNAME, new BasicAttributes());
    } catch (NamingException ne) {
      d = (DirContext) root.lookup(DIRNAME);
    }
    this.ctx = d;
    if (ctx == null) {
      throw new NullPointerException();
    }
  }

  public void configure(
      int httpPort,
      int httpsPort) throws IOException {

    InetAddress addr = InetAddress.getLocalHost();

    this.localTemplate = 
      new GlobalEntry(
          null,
          addr, httpPort,
          addr, httpsPort);

    // for now don't advertise the empty "host:port"
  }

  public void register(String name) throws IOException {
    // check the name
    if (name == null) {
      throw new NullPointerException();
    }

    // create a fully-specified entry
    GlobalEntry addE = 
      new GlobalEntry(
          name, 
          localTemplate.getHttpAddress(),
          localTemplate.getHttpPort(),
          localTemplate.getHttpsAddress(),
          localTemplate.getHttpsPort());

    try {
      ctx.rebind(name, addE, new BasicAttributes());
    } catch (NamingException ne) {
      throw new IOException(ne.getMessage());
    }
  }

  public void unregister(String name) throws IOException {
    // check the name
    if (name == null) {
      throw new NullPointerException();
    }

    // FIXME verify that "name" was registered by this instance

    try {
      ctx.unbind(name);
    } catch (NamingException ne) {
      throw new IOException(ne.getMessage());
    }
  }

  public GlobalEntry find(final String name) throws IOException {
    // check name
    if ((name == null) ||
        (name.length() == 0)) {
      return null;
    }

    // FIXME cache?  timer thread to update the cache?

    GlobalEntry ret;
    try {
      ret = (GlobalEntry) ctx.lookup(name);
    } catch (NameNotFoundException nnfe) {
      return null;
    } catch (NamingException ne) {
      throw new IOException(ne.getMessage());
    }

    return ret;
  }
 
  public List listNames() throws IOException {
    return listNames(new ArrayList());
  }

  public List listNames(
      List toList) throws IOException {
    // check destination list
    if (toList == null) {
      throw new NullPointerException();
    }

    try {
      NamingEnumeration en = ctx.list("");
      while (en.hasMoreElements()) {
        NameClassPair ncp = (NameClassPair) en.nextElement();
        if (!(ncp.getClassName().equals(GLOBAL_ENTRY_CLASSNAME))) {
          continue;
        }
        toList.add(ncp.getName());
      }
    } catch (NamingException ne) {
      throw new IOException(ne.getMessage());
    }

    return toList;
  }
 
  public List findAll(GlobalEntry query) throws IOException {
    return findAll(new ArrayList(), query);
  }

  public List findAll(
      List toList,
      GlobalEntry query) throws IOException {
    // check destination list
    if (toList == null) {
      throw new NullPointerException();
    }

    // FIXME cache?  timer thread to update the cache?

    try {
      NamingEnumeration en = ctx.listBindings("");
      while (en.hasMoreElements()) {
        Binding binding = (Binding) en.nextElement();
        if (!(binding.getClassName().equals(GLOBAL_ENTRY_CLASSNAME))) {
          continue;
        }
        GlobalEntry ge = (GlobalEntry) binding.getObject();
        if (ge.matches(query)) {
          toList.add(ge);
        }
      }
    } catch (NamingException ne) {
      throw new IOException(ne.getMessage());
    }

    return toList;
  }
}
