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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of PrefixTable API.
 * <p>
 * This is a lazy implementation with a flat list of (String, Object) 
 * pairs.  It's probably fine, since we expect these tables to be
 * relatively small (~10 entries).
 */
public class PrefixTableImpl implements PrefixTable {

  // List<Entry>
  private final List l = new ArrayList();

  public Object match(String input) {
    if (input != null) {
      int il = input.length();
      synchronized (l) {
        for (int i = 0, n = l.size(); i < n; i++) {
          Entry ei = (Entry) l.get(i);
          String pi = ei.prefix;
          int pil = pi.length();
          if (il >= pil && input.startsWith(pi)) {
            if (il == pil) {
              return ei.value;
            } else if (input.charAt(pil) == '/') {
              return new PrefixMatch(
                  input.substring(0, pil),
                  input.substring(pil),
                  ei.value);
            }
          }
        }
      }
    }
    return null;
  }

  public List list() {
    synchronized (l) {
      List ret = new ArrayList(l.size());
      for (int i = 0, n = l.size(); i < n; i++) {
        Entry ei = (Entry) l.get(i);
        ret.add(ei.prefix);
      }
      return ret;
    }
  }

  public boolean add(String prefix, Object value) {
    if (prefix == null || value == null) {
      throw new IllegalArgumentException("null arg");
    }
    synchronized (l) {
      if (match(prefix) != null) {
        return false;
      }
      l.add(new Entry(prefix, value));
      return true;
    }
  }

  public Object remove(String prefix) {
    if (prefix != null) {
      synchronized (l) {
        for (int i = 0, n = l.size(); i < n; i++) {
          Entry ei = (Entry) l.get(i);
          if (prefix.equals(ei.prefix)) {
            l.remove(i);
            return ei.value;
          }
        }
      }
    }
    return null;
  }

  public List removeAll() {
    synchronized (l) {
      int n = l.size();
      if (n == 0) {
        return Collections.EMPTY_LIST;
      } else {
        List ret = new ArrayList(n);
        for (int i = 0; i < n; i++) {
          Entry ei = (Entry) l.get(i);
          ret.add(ei.value);
        }
        l.clear();
        return ret;
      }
    }
  }

  public String toString() {
    synchronized (l) {
      return l.toString();
    }
  }

  private static class Entry {
    public final String prefix;
    public final Object value;
    public Entry(String prefix, Object value) {
      this.prefix = prefix;
      this.value = value;
    }
    public String toString() {
      return "("+prefix+", "+value+")";
    }
  }
}
