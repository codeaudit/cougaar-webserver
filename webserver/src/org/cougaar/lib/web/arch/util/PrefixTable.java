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

import java.util.List;

/**
 * A table of (prefix --&gt;  object) entries, suitable for
 * Servlet PATH_INFO lookups.
 * <p>
 * Each prefix <code>match(input)</code> lookup supports either an
 * exact match or a prefix match + "/" + optional additional text.
 * (I.e. the regular expression: <code>prefix(/.*)?</code>).
 * <p>
 * The table maintains the insertion ordering.
 * <p>
 * For example, the table could contain:<ol>
 *   <li>add("/foo/x", xObj)</li>
 *   <li>add("/bar", barObj)</li>
 *   <li>add("/foo", fooObj)</li>
 * </ol>
 * where:<ul>
 *   <li>match("/foo")     --&gt; fooObj</li>
 *   <li>match("/foo/")    --&gt; {fooObj, "/"}</li>
 *   <li>match("/foo/abc") --&gt; {fooObj, "/abc"}</li>
 *   <li>match("/fooblah") --&gt; null</li>
 *   <li>match("/foo/x")   --&gt; xObj</li>
 *   <li>match("/foo/x/")  --&gt; {xObj, "/"}</li>
 *   <li>match("/foo/x/y") --&gt; {xObj, "/y"}</li>
 *   <li>match("/foo/xyz") --&gt; {fooObj, "/xyz"}</li>
 *   <li>match("/bar")     --&gt; barObj</li>
 *   <li>match("/bark")    --&gt; null</li>
 *   <li>match("/junk")    --&gt; null</li>
 * </ul>
 */
public interface PrefixTable {

  /**
   * @return null if no match, PrefixMatch if input contains the
   * prefix + "/" + optional additional text, or Object if the
   * input exactly matches a registered prefix.
   */
  Object match(String input);

  /**
   * @return List of prefix entries
   */
  List list();

  /**
   * @return true if added
   */
  boolean add(String prefix, Object value);

  /**
   * @return non-null value if prefix was listed
   */
  Object remove(String prefix);

  /**
   * @return List of values
   */
  List removeAll();

}
