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
