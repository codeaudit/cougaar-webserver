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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses a URL path into its name, is_node_of, options, and subpath.
 * <p>
 * "is_node_of" is a special flag.  It's equivalent short-hand is "~".  It
 * takes precidence over all other options.
 * <p>
 * For example, "/$(a,b)~foo/bar" is parsed into:<pre>
 *   name:         "foo"
 *   is_node_of:   true
 *   options:      ["a", "b"]
 *   subpath:      "/bar"
 * </pre> 
 * In the above example, if "foo" is local to our node then we'll invoke
 * our <i>node's</i> "/bar" servlet, otherwise we'll attempt the "a"
 * redirector then the "b" redirector.
 * <p>
 * Additional examples:<ol>
 *   <li>"/$x"      == local x, else use default redirectors</li>
 *   <li>"/$()x"    == local x, else fail</li>
 *   <li>"/$~x"     == local node if x is local, else use default redirectors</li>
 *   <li>"/$(~)x"   == local node if x is local, else use default redirectors</li>
 *   <li>"/$()~x"   == local node if x is local, else fail</li>
 *   <li>"/$!x"     == local x, else use "!" redirector</li>
 *   <li>"/$(!)x"   == local x, else use "!" redirector</li>
 *   <li>"/$(!)~x"  == local node if x is local, else use "!" redirector</li>
 *   <li>"/$(!,~)x" == local node if x is local, else use "!" redirector</li>
 * </ol>
 * <p>
 * We separate out our {@link #isNodeOf} flag from the {@link #getOptions} to
 * distinguish between "/$~x" and "/$()~x".  If the "~" was in the options list
 * then we couldn't distinguish between these two cases.
 */
public final class PathParser {

  private final String encName;
  private final boolean is_node_of;
  private final List options;
  private final String subpath;

  public PathParser(String path) {
    int pathLength = (path == null ? 0 : path.length());

    // look for "/$" prefix
    if (pathLength < 2 ||
        path.charAt(0) != '/' ||
        path.charAt(1) != '$') {
      // name not specified
      encName = null;
      is_node_of = false;
      options = null;
      subpath = path;
      return;
    }

    // parse "/$" + (options+name) + ["/" subpath]
    int sep = path.indexOf('/', 1);
    if (sep < 0) {
      sep = pathLength;
    }
    this.subpath = (sep >= pathLength ? "" : path.substring(sep));
    String enc = path.substring(2, sep);

    char ch = (enc.length() > 0 ? enc.charAt(0) : 'a');
    if ((ch >= 'a' && ch <= 'z') ||
        (ch >= 'A' && ch <= 'Z') ||
        (ch >= '0' && ch <= '9')) {
      // no options
      this.encName = enc;
      this.is_node_of = false;
      this.options = null;
      return;
    }

    // separate options from name
    //
    // Options are either leading control characters ([^a-zA-Z0-9])
    // or comma-separated strings within a "(..)" block.  Whitespace is
    // ignored.
    //
    // Examples:
    //   "foo"    -> [] and "foo"
    //   "_foo"   -> ["_"] and "foo"
    //   "(_)foo" -> ["_"] and "foo"
    //   "_^foo"  -> ["_", "^"] and "foo"
    //   "(alpha,beta)foo" -> ["alpha", "beta"] and "foo"
    String s = decode(enc);
    boolean node_of = false;
    int len = s.length();
    int i;
    List l = null;
    for (i = 0; i < len; i++) {
      ch = s.charAt(i);
      if ((ch >= 'a' && ch <= 'z') ||
          (ch >= 'A' && ch <= 'Z') ||
          (ch >= '0' && ch <= '9')) {
        // end of options
        break;
      }
      if (ch == ' ' || ch == '\t') {
        continue;
      }
      if (ch != '(') {
        if (ch == '~') {
          node_of = true;
        } else {
          if (l == null) {
            l = new ArrayList();
          }
          l.add(String.valueOf(ch));
        }
        continue;
      }
      // find end ')', tokenize
      i++;
      int k = s.indexOf(')', i);
      if (k < 0) {
        k = len;
      }
      if (l == null) {
        // ensure that l is non-null, to act as an explicit non-default
        // "empty" options case
        l = new ArrayList();
      }
      while (true) {
        int j = s.indexOf(',', i);
        boolean end = (j < 0 || j >= k);
        if (end) {
          j = k;
        }
        String s2 = s.substring(i, j).trim();
        if (s2.length() > 0) {
          if ("~".equals(s2) || "is_node_of".equals(s2)) {
            node_of = true;
          } else {
            l.add(s2);
          }
        }
        if (end) {
          break;
        }
        i = j+1;
      }
      i = k;
    }
    this.encName = (i < len ? encode(s.substring(i)) : null);
    this.is_node_of = node_of;
    this.options = (l == null ? null : Collections.unmodifiableList(l));
  }

  public String getName() { return encName; }
  public boolean isNodeOf() { return is_node_of; }
  public List getOptions() { return options; }
  public String getSubpath() { return subpath; }

  public String toString() {
    return 
      "(path"+
      " name="+encName+
      " is_node_of="+is_node_of+
      " options="+options+
      " subpath="+subpath+")";
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
