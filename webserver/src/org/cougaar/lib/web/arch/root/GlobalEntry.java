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
package org.cougaar.lib.web.arch.root;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;

/**
 * Global entry for the mapping of a "name" to a unique
 * server (both HTTP and HTTP urls), such as:<pre>
 *    ("coug" -&gt; "www.cougaar.org, 80, www.cougaar.org, 443")
 * </pre>.
 *
 * @see #GlobalRegistry.find(String)
 */
public final class GlobalEntry 
implements Serializable, Cloneable {

  private final String name;

  // short-hand:
  //   ha -> HTTP  address
  //   hp -> HTTP  port
  //   sa -> HTTPS address
  //   sp -> HTTPS port
  private final InetAddress ha;
  private final int         hp;
  private final InetAddress sa;
  private final int         sp;

  /**
   * See the various "get*()" methods for details.
   */
  public GlobalEntry(
      final String name,
      final InetAddress httpAddr,
      final int httpPort,
      final InetAddress httpsAddr,
      final int httpsPort) {
    this.name = name;
    this.ha = httpAddr;
    this.hp = httpPort;
    this.sa = httpsAddr;
    this.sp = httpsPort;
  }

  /**
   * Equivalent to 
   * <pre><tt>
   *  (isNameWildcard() ||
   *   isHttpAddressWildcard() ||
   *   isHttpPortWildcard() ||
   *   isHttpsAddressWildcard() ||
   *   isHttpsPortWildcard())</tt></pre>
   */
  public boolean hasWildcard() {
    return
      ((name == null) ||
       (ha == null) ||
       (hp <= 0) ||
       (sa == null) ||
       (sp <= 0));
  }

  /**
   * Get the name of a registered entry, or <tt>null</tt> if used
   * as a wildcard query.
   */
  public String getName() {
    return name;
  }

  /**
   * Equivalent to <tt>(getName() == null)</tt>.
   */
  public boolean isNameWildcard() {
    return (name == null);
  }

  /**
   * Get the HTTP host address of a server, or <tt>null</tt> if 
   * used as a wildcard query.
   */
  public InetAddress getHttpAddress() {
    return ha;
  }

  /**
   * Equivalent to <tt>(getHttpAddress() == null)</tt>.
   */
  public boolean isHttpAddressWildcard() {
    return (ha == null);
  }

  /**
   * Get the HTTP port address of a server, or a negative 
   * <tt>int</tt> if used as a wildcard query.
   */
  public int getHttpPort() {
    return hp;
  }

  /**
   * Equivalent to <tt>(getHttpPort() &lt;= 0)</tt>.
   */
  public boolean isHttpPortWildcard() {
    return (hp <= 0);
  }

  /**
   * Get the HTTPS host address of a server, or <tt>null</tt> if 
   * used as a wildcard query.
   */
  public InetAddress getHttpsAddress() {
    return sa;
  }

  /**
   * Equivalent to <tt>(getHttpsAddress() == null)</tt>.
   */
  public boolean isHttpsAddressWildcard() {
    return (sa == null);
  }

  /**
   * Get the HTTPS port address of a server, or a negative 
   * <tt>int</tt> if used as a wildcard query.
   */
  public int getHttpsPort() {
    return sp;
  }

  /**
   * Equivalent to <tt>(getHttpsPort() &lt;= 0)</tt>.
   */
  public boolean isHttpsPortWildcard() {
    return (sp <= 0);
  }

  public Object clone() {
    try { 
      // can we clone an InetAddress?
      return super.clone();
    } catch (CloneNotSupportedException e) { 
      // this shouldn't happen, since we are Cloneable
      throw new InternalError();
    }
  }

  /**
   * Test to see if this <code>GlobalEntry</code> matches the 
   * "wild" pattern of the given <tt>ge</tt> -- if the given
   * <tt>ge</tt> is not wild, then this is equivalent to
   * <tt>this.equals(ge)</tt>.
   */
  public boolean matches(GlobalEntry ge) {
    return
      ((ge == null) ||
       ((((ge.name) == null) ||
         ((ge.name).equals(this.name))) &&
        (((ge.ha) == null) ||
         ((ge.ha).equals(this.ha))) &&
        (((ge.hp) <= 0) ||
         ((ge.hp) == (this.hp))) &&
        (((ge.sa) == null) ||
         ((ge.sa).equals(this.sa))) &&
        (((ge.sp) <= 0) ||
         ((ge.sp) == (this.sp)))));
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof GlobalEntry)) {
      return false;
    }
    GlobalEntry ge = (GlobalEntry)o;
    return
      (((name == null) ?
        (ge.name == null) :
        (name.equals(ge.name))) &&
       ((ha == null) ?
        (ge.ha == null) :
        (ha.equals(ge.ha))) &&
       ((hp <= 0) ?
        (ge.hp <= 0) :
        (hp == ge.hp)) &&
       ((sa == null) ?
        (ge.sa == null) :
        (sa.equals(ge.sa))) &&
       ((sp <= 0) ?
        (ge.sp <= 0) :
        (sp == ge.sp)));
  }

  public String toString() {
    return
      "("+
      ((name == null) ? "*" : name) +
      "-> (http://"+
      ((ha == null) ? "*" : ha.toString())+
      ":"+
      ((hp <= 0) ? "*" : Integer.toString(hp))+
      ", https://"+
      ((sa == null) ? "*" : sa.toString())+
      ":"+
      ((sp <= 0) ? "*" : Integer.toString(sp))+
      "))";
  }

  private static final long serialVersionUID = 1071097574892839992L;
}
