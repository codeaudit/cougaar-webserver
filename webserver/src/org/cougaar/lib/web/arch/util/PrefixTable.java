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
