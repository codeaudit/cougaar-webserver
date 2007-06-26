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

package org.cougaar.lib.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.lib.web.arch.ServletRegistry;
import org.cougaar.lib.web.arch.root.GlobalRegistry;

/**
 * A servlet that handles all "/agents" requests by optionally
 * displaying locally-registered names or globally-registered names
 * (in the nameserver).
 * <p>
 * The supported URL-parameter arguments are:<ul>
 *   <li>"?suffix=" -- list the local agent names (default)</li>
 *   <li>"?suffix=<i>text</i>" -- list the agent names in the
 *       global white pages with the matching suffix, where "."
 *       is the root.  If the suffix doesn't start with ".",
 *       the substring starting at the first "." is used, and
 *       if there is no "." then the root (".") is used.</li>
 *   <li>"?format=<i>text</i>" -- specify the output format,
 *        where the options are:
 *        <ul>
 *          <li>"html" (default)</li>
 *          <li>"text" for plain text, one agent name per line</li>
 *          <li>"input" for HTML text form for typing in an agent
 *              name, plus a link to the "?format=select" page.
 *              See the javascript notes below.</li>
 *          <li>"select" for an interactive html form that uses
 *              javascript reloading.  This can be embedded in an
 *              html frame or popup window.  The selected name
 *              can be accessed by using javascript:<pre>
 *              var name = top.<i>frame</i>.document.agent.name.value;
 *              </pre>  This only works if both frames were generated
 *              by the same <i>host:port</i>, otherwise javascript will
 *              throw a "permission denied" error.</li>
 *        </ul></li> 
 *   <li>"?depth=<i>int</i> -- if the suffix is specified, this
 *       limits the recursion depth, where -1 is no limit (default
 *       is specified by the "-Dorg.cougaar.lib.web.list.depth"
 *       system property, which defaults to 1)</li>
 *   <li>"?size=<i>int</i> -- limit the list length, where -1 is
 *       no limit (default is specified by the
 *       "-Dorg.cougaar.lib.web.list.size" system property,
 *       which defaults to -1)</li>
 *   <li>"?time=<i>long</i> -- if the suffix is specified, this
 *       limits any single lookup time in milliseconds, where 0
 *       is no limit and -1 is cache-only (default is specified by
 *       the "-Dorg.cougaar.lib.web.list.timeout" system property,
 *       which defaults to 0)</li>
 *   <li>"?sorted=<i>boolean</i>  -- sort the names in alphabetical
 *       order (default is "true")</li>
 *   <li>"?split=<i>boolean</i>  -- for "?format=html", should links
 *       be split along hierarchy levels (default is "true")</li>
 *   <li>"?scope=all"   -- backwards compatibility for listing
 *       agents, equivalent to "?suffix=."</li>
 * </ul>
 * <p>
 * For example: "/agents?suffix=.&amp;format=text"
 * <p>
 * Note this nice feature: With the normal "/$name" redirects and this
 * servlet, the client can request "/$name/agents" to list all
 * co-located agents, regardless of where the named agent happens to
 * be located.
 *
 * @property org.cougaar.lib.web.list.split=true
 *   "/agents" servlet boolean to split HTML links by "." separator
 *   for per-level "?suffix=" links.  Defaults to "true".
 * @property org.cougaar.lib.web.list.depth=5
 *   "/agents" servlet recursion depth for white pages listings,
 *   where -1 indicates no limit.  Defaults to 5.
 * @property org.cougaar.lib.web.list.size=-1
 *   "/agents" servlet size limit for white pages listings,
 *   where -1 indicates no limit.  Defaults to -1.
 * @property org.cougaar.lib.web.list.timeout=-1
 *   "/agents" servlet timeout in millseconds for white pages
 *   listings, where -1 indicates block forever.  Defaults to -1.
 */
public class AgentsServlet implements Servlet {

  private static final boolean SPLIT =
    SystemProperties.getBoolean("org.cougaar.lib.web.list.split", true);
  private static final int DEPTH =
    SystemProperties.getInt("org.cougaar.lib.web.list.depth", 5);
  private static final int SIZE =
    SystemProperties.getInt("org.cougaar.lib.web.list.size", -1);
  private static final long TIME =
    SystemProperties.getLong("org.cougaar.lib.web.list.timeout", -1);

  private static final String path = "/agents";

  private final String localNode;

  // read-only registries:
  private final ServletRegistry localReg;
  private final GlobalRegistry globReg;

  public AgentsServlet(
      String localNode,
      ServletRegistry localReg,
      GlobalRegistry globReg) {
    this.localNode = localNode;
    this.localReg = localReg;
    this.globReg = globReg;

    String s =
      (localNode == null ? "localNode" :
       localReg == null ? "localReg" :
       globReg == null ? "globReg" :
       null);
    if (s != null) {
      throw new IllegalArgumentException("null "+s);
    }
  }

  public void service(
      ServletRequest sreq, ServletResponse sres
      ) throws ServletException, IOException {

    HttpServletRequest req = (HttpServletRequest) sreq;
    HttpServletResponse res = (HttpServletResponse) sres;

    MyHandler h = new MyHandler(localNode, localReg, globReg);
    h.execute(req, res);
  }

  private static class MyHandler {

    private final String localNode;
    private final ServletRegistry localReg;
    private final GlobalRegistry globReg;

    private String encSuffix;
    private String encName;
    private boolean isLocal;

    private boolean useHtml;
    private boolean useInput;
    private boolean useSelect;

    private int depthLimit;
    private int sizeLimit;
    private long timeLimit;

    private boolean sorted;
    private boolean split;

    private String serverName;
    private int serverPort;

    public MyHandler(
        String localNode,
        ServletRegistry localReg,
        GlobalRegistry globReg) {
      this.localNode = localNode;
      this.localReg = localReg;
      this.globReg = globReg;
    }

    public void execute(
        HttpServletRequest req, 
        HttpServletResponse res) throws IOException {
      parseParams(req);
      List names = new ArrayList();
      Limit lim = listNames(names);
      showNames(res, names, lim);
    }

    private void parseParams(HttpServletRequest req) {
      // global url-encoded suffix (default is "")
      encSuffix = req.getParameter("suffix");
      encName = encSuffix;
      isLocal = (encSuffix == null || encSuffix.length() == 0);
      if (isLocal) {
        encSuffix = ".";
      } else {
        int j = encSuffix.indexOf('.');
        if (j < 0) {
          encSuffix = ".";
        } else if (j == 0) {
          encName = null;
        } else {
          encSuffix = encSuffix.substring(j);
        }
      }

      // html v.s. plain-text response
      String format = req.getParameter("format");
      useHtml = (!("text".equals(format)));
      useInput = "input".equals(format);
      useSelect = "select".equals(format);

      // limits
      String s_depthLimit = req.getParameter("depth"); 
      depthLimit = 
        (s_depthLimit == null ?
         (DEPTH) :
         Integer.parseInt(s_depthLimit));
      String s_sizeLimit = req.getParameter("size"); 
      sizeLimit = 
        (s_sizeLimit == null ?
         (SIZE) :
         Integer.parseInt(s_sizeLimit));
      String s_timeLimit = req.getParameter("time"); 
      timeLimit = 
        (s_timeLimit == null ?
         (TIME) :
         Long.parseLong(s_timeLimit));

      // sorted v.s. unsorted response
      //
      // TODO support option to sort by suffix, e.g.:
      //    [ "x.a", "z.a", "y.b" ]
      // instead of alphabetical, which in this case would split ".a":
      //    [ "x.a", "y.b", "z.a" ]
      sorted = (!("false".equals(req.getParameter("sorted"))));

      // split HTML links
      String s_split = req.getParameter("split");
      split = (s_split == null ? SPLIT : "true".equals(s_split));

      // backwards compatibility:
      if ("all".equals(req.getParameter("scope"))) {
        isLocal = false;
      }

      // server name & port
      serverName = req.getServerName();
      serverPort = req.getServerPort();
    }

    private Limit listNames(List toList) {
      toList.clear();
      // get the listing
      if (useInput || sizeLimit == 0) {
        // none
        return null; 
      }
      Limit lim = null;
      if (isLocal) {
        // local names
        toList.addAll(localReg.listNames());
      } else {
        long deadline;
        if (timeLimit < 0) {
          // no limit
          deadline = -1; 
        } else if (timeLimit == 0) {
          // cache-only
          deadline = 0;
        } else { 
          deadline = System.currentTimeMillis() + timeLimit;
          if (deadline <= 0) {
            // fix wrap-around
            deadline = -1;
          }
        }
        lim = 
          listRecurse(
              toList,
              encSuffix,
              0,
              deadline);
      }
      if (sizeLimit > 0) {
        int i = toList.size();
        if (i > sizeLimit) {
          Collections.sort(toList);
          while (--i >= sizeLimit) {
            toList.remove(i);
          }
          if (lim == null) {
            lim = Limit.SIZE;
          }
        }
      } else if (sorted) {
        Collections.sort(toList);
      }
      return lim;
    }

    // recursive!
    private Limit listRecurse(
        List toList, 
        String encS,
        int depth,
        long deadline) {
      int size = toList.size();
      if (depthLimit >= 0 && depth >= depthLimit) {
        // reached max depth, add suffix
        if (sizeLimit >= 0 && size >= sizeLimit) {
          return Limit.SIZE;
        }
        // obvious depth limit if any entry starts with "."
        toList.add(encS);
        return Limit.DEPTH;
      }
      // list names at this depth level
      long t;
      if (deadline < 0) {
        // no deadline
        t = 0;
      } else if (deadline == 0) {
        // cache-only
        t = -1; 
      } else {
        t = deadline - System.currentTimeMillis();
        if (t < 0) {
          // ran out of time, don't switch to cache-only
          return new Limit.Failed(null, encS, t, timeLimit);
        }
      }
      Set encNames;
      try {
        encNames = globReg.list(encS, t);
      } catch (Exception e) {
        return new Limit.Failed(e, encS, t, timeLimit);
      }
      // sort, to preserve sizeLimit order
      //
      // note that this sort controls the recursion order, which will
      // sort by suffix.  If "&sort=true" is specified then the full
      // result will be further sorted by prefix.
      List l = new ArrayList(encNames);
      Collections.sort(l);
      Limit lim = null;
      for (int i = 0, n = l.size(); i < n; i++) {
        String s = (String) l.get(i);
        if (s == null) {
          continue;
        }
        if (sizeLimit >= 0 && size >= sizeLimit) {
          // reached max count
          return Limit.SIZE;
        }
        if (s.length() > 0 && s.charAt(0) == '.') {
          // recurse!
          Limit lim2 = 
            listRecurse(
                toList,
                s,
                (depth + 1),
                deadline);
          if (lim2 != null && lim2 != Limit.DEPTH) {
            return lim2;
          }
          if (lim == null) {
            lim = lim2;
          }
          size = toList.size();
        } else {
          toList.add(s);
        }
      }
      return lim;
    }

    private void showNames(
        HttpServletResponse res,
        List names,
        Limit lim) throws IOException {
      if (lim == Limit.DEPTH) {
        // ignore depth limit, since it's obvious if any entries
        // start with a "."
        lim = null;
      } else if (
          (lim instanceof Limit.Failed) &&
          useSelect) {
        // discard partial results (!)
        // use input field instead of drop-down list
        useSelect = false;
        useInput = true;
      }

      // write response
      res.setContentType(
          (useHtml ? "text/html" : "text/plain"));
      PrintWriter out = res.getWriter();
      if (!(useHtml)) {
        listPlain(out, names);
      } else if (useInput) {
        listInput(out);
      } else if (useSelect) {
        if (isLocal) {
          listSelectLocal(out, names);
        } else {
          listSelectAll(out, names);
        }
      } else {
        listHTML(out, names, lim);
      }
      out.close();
    }

    private void listPlain(
        PrintWriter out,
        List names) {
      // simple line-by-line output
      int n = names.size();
      if (n > 0) {
        Iterator iter = names.iterator();
        for (int i = 0; i < n; i++) {
          String ni = (String) iter.next();
          out.println(ni);
        }
      }
    }

    private void listInput(PrintWriter out) {
      // text box
      boolean isName = (encName != null);
      out.print(
          "<html><head>\n"+
          "<script language=\"JavaScript\">\n"+
          "<!--\n"+
          "function toSelect() {\n"+
          "  var val = document.agent.name.value;\n"+
          "  location.href="+
          getLink("\"+val+\"", "select")+
          ";\n"+
          "}\n"+
          "// this works on some browsers:\n"+
          "function noenter() {\n"+
          "  var key = 0;\n"+
          "  if (window.event) {\n"+
          "    if (navigator.appName == 'Netscape') {\n"+
          "      key = window.event.which;\n"+
          "    } else {\n"+
          "      key = window.event.keyCode;\n"+
          "    }\n"+
          "  }\n"+
          "  return (key != 13);\n"+
          "}\n"+
          "// -->\n"+
          "</script>\n"+
        "</head>\n"+
        "<body>\n"+
        "<form name=\"agent\"");
      // We don't want this target, but some browsers will accept
      // an ENTER in the text field as a submit.
      out.print(
          " target="+
          getLink(
            (isName ? encName : encSuffix),
            "input"));
      out.print(
          ">\n"+
          "<input type=\"text\" size=\"20\" name=\"name\" value=\""+
          (isName ? encName : encSuffix)+
          "\" onKeypress=\"noenter()\"> "+
          "<input type=\"button\" value=\"list\""+
          " onClick=\"toSelect()\">"+
          "</form></body></html>");
    }

    private void listSelectLocal(
        PrintWriter out,
        List names) {
      // local names
      out.print(
          "<html><head>\n"+
          "<script language=\"JavaScript\">\n"+
          "<!--\n"+
          "function selectAgent() {\n"+
          "  var idx = document.agent.select.selectedIndex;\n"+
          "  var val = document.agent.select.options[idx].text;\n"+
          "  document.agent.name.value = val;\n"+
          "}\n"+
          "function toText() {\n"+
          "  var val = document.agent.name.value;\n"+
          "  location.href="+
          getLink("\"+val+\"", "input")+
          ";\n"+
          "}\n"+
          "// -->\n"+
          "</script>\n"+
          "</head>\n"+
          "<body>\n"+
          "<form name=\"agent\" onSubmit=\";\">\n"+
          "<input type=\"hidden\" name=\"name\" value=\""+
          encSuffix+"\">\n"+
          "<select name=\"select\""+
          " onChange=\"selectAgent()\""+
        ">\n");
      // print names
      int n = names.size();
      if (n > 0) {
        Iterator iter = names.iterator();
        for (int i = 0; i < n; i++) {
          String ni = (String) iter.next();
          out.print(
              "<option>"+ni+"</option>\n");
        }
      }
      out.print(
          "</select> \n"+
          "<input type=\"button\" value=\"text\""+
          " onClick=\"toText()\">"+
          "</form>\n"+
          "</body></html>\n");
    }

    private void listSelectAll(
        PrintWriter out,
        List names) {
      // interactive javascript form
      boolean isName = (encName != null);
      boolean isRoot = ".".equals(encSuffix);
      out.print(
          "<html><head>\n"+
          "<script language=\"JavaScript\">\n"+
          "<!--\n"+
          "function selectAgent() {\n"+
          "  var idx = document.agent.select.selectedIndex;\n"+
          "  var val = document.agent.select.options[idx].text;\n"+
          "  document.agent.name.value = val;\n"+
          "  if (val.length > 0 &&\n"+
          "      val.charAt(0) == '.' &&\n"+
          "      val != \""+encSuffix+"\") {\n"+
          "    location.href="+
          getLink("\"+val+\"", "select")+
          ";\n"+
          "  }\n"+
          "}\n"+
          "function toText() {\n"+
          "  var val = document.agent.name.value;\n"+
          "  location.href="+
          getLink("\"+val+\"", "input")+
          ";\n"+
          "}\n"+
          "// -->\n"+
          "</script>\n"+
          "</head>\n"+
          "<body>\n"+
        "<form name=\"agent\" onSubmit=\";\">\n"+
        "<input type=\"hidden\" name=\"name\" value=\""+
        (isName ? encName : encSuffix)+
        "\">"+
        "<select name=\"select\""+
        " onChange=\"selectAgent()\""+
        ">\n");
      // print parents back to root
      out.print(
          "<option"+
          ((isRoot && !isName) ? " selected" : "")+
          ">.</option>\n");
      if (!isRoot) {
        // assert (encSuffix.startsWith("."));
        for (int j = encSuffix.length(); j > 0; ) {
          j = encSuffix.lastIndexOf('.', j-1);
          String s = encSuffix.substring(j);
          out.print(
              "<option"+
              ((j == 0 && !isName) ? " selected" : "") +
              ">"+s+"</option>\n");
        }
      }
      // print names
      int n = names.size();
      if (n > 0) {
        Iterator iter = names.iterator();
        for (int i = 0; i < n; i++) {
          String ni = (String) iter.next();
          out.print(
              "<option"+
              ((isName && encName.equals(ni)) ? " selected" : "")+
              ">"+ni+"</option>\n");
        }
      }
      out.print(
          "</select>\n"+
          "<input type=\"button\" value=\"text\""+
          " onClick=\"toText()\">"+
          "</form></body></html>\n");
    }

    private void listHTML(
        PrintWriter out,
        List names,
        Limit lim) {
      // pretty HTML
      int n = names.size();
      boolean isAll = (!isLocal && lim == null && ".".equals(encSuffix));
      if (isAll) {
        for (int i = 0; i < n; i++) {
          String ni = (String) names.get(i);
          if (ni.length() > 0 && ni.charAt(0) == '.') {
            isAll = false;
            break;
          }
        }
      }
      String title =
        (isLocal ? ("Local agents on node "+localNode) :
         isAll ? ("All agents in the society") :
         ("All agents "+
          (".".equals(encSuffix) ?
           ("at the Root (\"") :
           ("with Suffix (\""+createSuffixLinks(encSuffix)))+
          "<a href="+getLink(".", "html")+">.</a>\")"));
      out.print("<html><head><title>");
      out.print(title);
      out.print(
          "</title></head>\n"+
          "<body><p><h1>");
      out.print(title);
      out.print("</h1>\n");
      if (n > 0) {
        out.print("<table border=\"0\">\n");
        for (int i = 0; i < n; i++) {
          String ni = (String) names.get(i);
          int j = ni.indexOf('.');
          out.print(
              ((i > 0) ? "</td></tr>\n" : "")+
              "<tr><td align=\"right\">&nbsp;"+
              (j == 0 ? "<b>" : "")+
              (i + 1)+
              (j == 0 ? "</b>" : "")+
              ".&nbsp;</td><td align=\"right\">");
          if (split) {
            if (j != 0) {
              // print head(\.tail)?
              out.print(
                  "<a href=\"/$"+ni+"/list\">"+
                  (j < 0 ? ni : ni.substring(0, j))+
                  "</a>");
            } 
            if (j >= 0) {
              // print \.tail
              out.print(createSuffixLinks(ni.substring(j)));
            }
          } else {
            // print complete head(\.tail)?
            out.print(
                "<a href="+
                ((j == 0) ?
                 (getLink(ni, "html")) :
                 ("\"/$"+ni+"/list\""))+
                ">"+ni+"</a>");
          }
        }
        if (lim != null) {
          out.print(
              "<tr><td>&nbsp;</td><td align=\"left\">"+
              "<font color=\"red\">"+
              lim+
              "</font></td></tr>");
        }
        out.print("</table>\n");
      } else {
        out.print(
            "<font color=\"red\">zero agents found"+
            (lim == null ? "" : ("<br>"+lim))+
            "</font>");
      }
      out.print(
          "<p>\n"+
          "<a href="+
          getLink(null, "html")+
          "><b>Local</b> agents on node "+localNode+"</a><br>\n"+
          "<a href="+
          getLink(".", "html")+
          "><b>All</b> agents in the society</a><br>"+
          "</body></html>\n");
    }

    /** Create URI back to this servlet */
    private String getLink(String suffix, String format) { 
      return
        "\""+
        "/$"+localNode+
        "/agents"+
        "?suffix="+(suffix == null ? "" : suffix)+
        "&format="+format+
        "&depth="+depthLimit+
        "&size="+sizeLimit+
        "&time="+timeLimit+
        "&sorted="+sorted+
        "&split="+split+
        "\"";
    }

    /**
     * Given a suffix generate an HTML href list.
     * <p>
     * For example, given:<pre>
     *   .a.b.c
     * </pre>Generate:<pre>
     *   &lt;a href="?suffix=.a.b.c"&gt;.a&lt;/a&gt; <i>+</i>
     *   &lt;a href="?suffix=.b.c"&gt;.b&lt;/a&gt; <i>+</i>
     *   &lt;a href="?suffix=.c"&gt;.c&lt;/a&gt;
     * </pre>
     */
    private String createSuffixLinks(String encS) {
      // assert (encS.charAt(0) == '.');
      StringBuffer buf = new StringBuffer();
      int len = encS.length();
      for (int j = 0; j < len; ) {
        int k = encS.indexOf('.', j+1);
        if (k < 0) {
          if (j >= len) {
            break;
          }
          k = len;
        }
        buf.append("<a href=");
        buf.append(getLink(encS.substring(j), "html"));
        buf.append(">");
        buf.append(encS.substring(j,k));
        buf.append("</a>");
        j = k;
      }
      return buf.toString();
    }
  }

  //
  // other Servlet methods
  //

  private ServletConfig config;
  public void init(ServletConfig config) {
    this.config = config;
  }
  public ServletConfig getServletConfig() {
    return config;
  }
  public String getServletInfo() {
    return "agents-servlet";
  }
  public void destroy() {
    // ignore
  }

  private static abstract class Limit {
    public static final Limit SIZE = new Limit() {
      public String toString() {
        return "Reached size limit";
      }
    };
    public static final Limit DEPTH = new Limit() {
      public String toString() {
        return "Reached depth limit";
      }
    };

    public static class Failed extends Limit {
      public final Exception e;
      public final String encS;
      public long timeout;
      public long timeLimit;
      public Failed(
          Exception e,
          String encS,
          long timeout,
          long timeLimit) {
        this.e = e;
        this.encS = encS;
        this.timeout = timeout;
        this.timeLimit = timeLimit;
      }
      public String toString() {
        return 
          "Failed list (suffix="+encS+
          ", timeout="+timeout+
          ", timeLimit="+timeLimit+
          (e == null ? "" : ", exception="+e.getMessage())+
          ")";
      }
    }
  }
}
