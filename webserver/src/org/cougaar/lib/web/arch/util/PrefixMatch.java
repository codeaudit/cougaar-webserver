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
package org.cougaar.lib.web.arch.util;


/**
 * Returned by <code>PrefixTable.match(input)</code> if the
 * matched entry was not an exact match.
 * <p>
 * For example, if the table contains "/foo", and the
 * match input was "/foo/bar", the table will return
 * a PrefixMatch where <code>prefix=/foo &amp; tail=/bar</code>.
 */
public class PrefixMatch {
  private final String prefix;
  private final String tail;
  private final Object o;
  public PrefixMatch(
      String prefix,
      String tail,
      Object o) {
    this.prefix = prefix;
    this.tail = tail;
    this.o = o;
    if (prefix == null ||
        tail == null ||
        o == null) {
      throw new IllegalArgumentException("null arg");
    }
  }
  public String getPrefix() { return prefix; }
  public String getTail()   { return tail; }
  public Object getValue()  { return o; }
  public String toString() {
    return "("+prefix+", "+tail+", "+o+")";
  }
}
