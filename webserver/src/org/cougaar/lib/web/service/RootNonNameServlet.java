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
package org.cougaar.lib.web.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.lib.web.arch.root.GlobalRegistry;
import org.cougaar.lib.web.arch.ServletRegistry;

/**
 * A servlet that handles all non-"/$name" requests.
 * <p>
 * Specifically, this servlet handles:<ul>
 *   <li>"[/]"<br>
 *       Generates an html help page.</li><p>
 *   <li>"/$[/.*]"<br>
 *       Redirects the request to a <b>RANDOM</b> local agent name.
 *       This is useful when the client can assume that any
 *       locally-registered agent can handle the request.</li><p>
 *   <li>"/robots.txt"<br>
 *       Generates this hard-coded response:<pre>
 *           User-agent: *
 *           Disallow: /
 *       </pre>This keeps out web-crawlers, such as "google".</li><p>
 *   <li>"/agents[?args]"<br>
 *       Lists agent names on either the local host or in the global
 *       white pages.  The supported URL-parameter arguments are:<ul>
 *         <li>"?suffix=" -- list the local agent names (default)</li>
 *         <li>"?suffix=<i>text</i>" -- list the agent names in the
 *             global white pages with the matching suffix, where "."
 *             is the root.  If the suffix doesn't start with ".",
 *             the substring starting at the first "." is used, and
 *             if there is no "." then the root (".") is used.</li>
 *         <li>"?format=html" -- generate an html page (default)</li>
 *         <li>"?format=text  -- generate plain text, one agent name
 *             per line</li>
 *         <li>"?format=input -- generate an HTML text form for
 *             typing in an agent name, plus a link to the
 *             "?format=select" page.  See the javascript notes
 *             below.</li>
 *         <li>"?format=select  -- generate an interactive html form
 *             that uses javascript reloading.  This can be embedded
 *             in an html frame or popup window.  The selected name
 *             can be accessed by using javascript:<pre>
 *               var name = top.<i>frame</i>.document.agent.name.value;
 *             </pre>  This only works if both frames were generated
 *             by the same <i>host:port</i>, otherwise javascript will
 *             throw a "permission denied" error.</li>
 *         <li>"?sorted=true  -- sort the names in alphabetical order
 *             (default)</li>
 *         <li>"?sorted=false" -- don't sort the names</li>
 *         <li>"?scope=all"   -- backwards compatibility for listing
 *             agents, equivalent to "?suffix=."</li>
 *       </ul>
 *       <p>
 *       For example: "/agents?suffix=.&amp;format=text"
 *       <p>
 *       Note this nice feature: With the normal "/$name" redirects
 *       and this "/agents" support, the client can request
 *       "/$name/agents" to list all co-located agents, regardless of
 *       where the named agent happens to be located.</li><p>
 *   <li>"/<i>other</i>"<br>
 *       Same as "/$/<i>other</i>", assuming that "/<i>other</i>"
 *       doesn't match one of the cases listed above.
 *       <p>
 *       I'd like to remove this case and force users to <i>always</i>
 *       specify "/$/[.*]", but for now this is necessary for
 *       backwards compatibility.  Consider it <i>deprecated</i>...
 * </ul>
 */
public class RootNonNameServlet 
implements Servlet {

  private static final Random rand = new Random();

  // read-only registries:
  private ServletRegistry localReg;
  private GlobalRegistry globReg;

  public RootNonNameServlet(
      ServletRegistry localReg,
      GlobalRegistry globReg) {
    this.localReg = localReg;
    this.globReg = globReg;

    // null-check
    if ((localReg == null) ||
        (globReg == null)) {
      throw new NullPointerException();
    }
  }

  public void service(
      ServletRequest req,
      ServletResponse res) throws ServletException, IOException {

    HttpServletRequest httpReq;
    HttpServletResponse httpRes;
    try {
      httpReq = (HttpServletRequest) req;
      httpRes = (HttpServletResponse) res;
    } catch (ClassCastException cce) {
      // not an HTTP request?
      throw new ServletException("non-HTTP request or response");
    }

    handle(httpReq, httpRes);
  }

  private final void handle(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    String path = req.getRequestURI();
    // assert ((path != null) && (path.startsWith("/")))
    String trimPath;
    if (path.startsWith("/$")) {
      trimPath = path.substring(2);
    } else if (path.equals("/agents")) {
      listAgents(req, res);
      return;
    } else if (path.length() <= 2) {
      displayHelp(req, res);
      return;
    } else if (path.equals("/robots.txt")) {
      excludeRobots(req, res);
      return;
    } else {
      trimPath = path;
    }
    // assert (trimPath.startsWith("/"))
    randomLocalRedirect(req, res, trimPath);
  }

  private final void displayHelp(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {
    res.setContentType("text/html");
    PrintWriter out = res.getWriter();
    out.print(
      "<html><head><title>"+
      "Welcome to Cougaar"+
      "</title></head><body>\n"+
      "<h2>Welcome to "+
      "<a href=\"http://www.cougaar.org\">Cougaar</a></h2>\n"+
      "Options:<ul>\n"+
      "<li><a href=\"/agents\">Agents on host ("+
      req.getServerName()+":"+req.getServerPort()+
      ")</a></li>\n"+
      "<li><a href=\"/agents?suffix=.\">Agents at the root (.)</a>"+
      "</li>\n"+
      "</ul>\n"+
      "</body></html>");
    out.close();
  }

  private final void listAgents(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {

    // scan url-parameters for:
    //   "?format=[text|html|select]"
    //   "?sorted=[true|false]"
    //   "?suffix=[text]"

    // html v.s. plain-text response
    String format = req.getParameter("format");
    boolean useHtml = (!("text".equals(format)));
    boolean useInput = "input".equals(format);
    boolean useSelect = "select".equals(format);
    // sorted v.s. unsorted response
    boolean sorted = (!("false".equals(req.getParameter("sorted"))));
    // global url-encoded suffix (default is "")
    String encSuffix = req.getParameter("suffix");
    String encName = encSuffix;
    boolean isLocal = (encSuffix == null || encSuffix.length() == 0);
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

    // backwards compatibility:
    if ("all".equals(req.getParameter("scope"))) {
      isLocal = false;
    }

    // get the listing
    Collection names;
    if (isLocal) {
      names = localReg.listNames();
    } else {
      names = globReg.list(encSuffix);
    }
    if (names == null) {
      names = Collections.EMPTY_LIST;
    }

    if (sorted && !names.isEmpty()) {
      List l;
      if (names instanceof List) {
        l = (List) names;
      } else {
        l = new ArrayList(names);
        names = l;
      }
      Collections.sort(l);
    }

    // write response
    res.setContentType(
        (useHtml ? "text/html" : "text/plain"));
    PrintWriter out = res.getWriter();
    if (!(useHtml)) {
      listPlain(out, names);
    } else if (useInput) {
      listInput(out, names, encSuffix, encName, isLocal);
    } else if (useSelect) {
      if (isLocal) {
        listSelectLocal(out, names, encSuffix);
      } else {
        listSelectAll(out, names, encSuffix, encName);
      }
    } else {
      listHTML(
          out, names,
          isLocal, encSuffix, 
          req.getServerName(), req.getServerPort());
    }
    out.close();
  }
  
  private static final void listPlain(
      PrintWriter out,
      Collection names) {
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

  private static final void listInput(
      PrintWriter out,
      Collection names,
      String encSuffix,
      String encName,
      boolean isLocal) {
    // text box
    boolean isName = (encName != null);
    out.print(
        "<html><head>\n"+
        "<script language=\"JavaScript\">\n"+
        "<!--\n"+
        "function toSelect() {\n"+
        "  var val = document.agent.name.value;\n"+
        "  location.href=\"/agents?format=select&suffix=\"+val;\n"+
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
        " target=\"/agents?format=input&suffix="+
        (isName ? encName : encSuffix)+
        "\"");
    out.print(
        ">\n"+
        "<input type=\"text\" size=\"20\" name=\"name\" value=\""+
        (isName ? encName : encSuffix)+
        "\" onKeypress=\"noenter()\"> "+
        "<input type=\"button\" value=\"list\""+
        " onClick=\"toSelect()\">"+
        "</form></body></html>");
  }

  private static final void listSelectLocal(
      PrintWriter out,
      Collection names,
      String encSuffix) {
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
        "  location.href=\"/agents?format=input&suffix=\"+val;\n"+
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

  private static final void listSelectAll(
      PrintWriter out,
      Collection names,
      String encSuffix,
      String encName) {
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
        "    location.href=\"/agents?format=select&suffix=\"+val;\n"+
        "  }\n"+
        "}\n"+
        "function toText() {\n"+
        "  var val = document.agent.name.value;\n"+
        "  location.href=\"/agents?format=input&suffix=\"+val;\n"+
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

  private static final void listHTML(
      PrintWriter out,
      Collection names,
      boolean isLocal,
      String encSuffix,
      String serverName,
      int serverPort) {
    // pretty HTML
    String title;
    String suffixLinks = null;
    if (isLocal) {
      title =
        "Agent on Host ("+
        serverName+":"+serverPort+
        ")";
    } else {
      if (".".equals(encSuffix)) {
        title = 
          "Agents at the Root (\""+
          "<a href=\"/agents?suffix=.\">.</a>"+
          "\")";
      } else {
        suffixLinks = createSuffixLinks(encSuffix);
        title = "Agents with Suffix (\""+suffixLinks+"\")";
      }
    }
    out.print("<html><head><title>");
    out.print(title);
    out.print(
        "</title></head>\n"+
        "<body><p><h1>");
    out.print(title);
    out.print("</h1>\n");
    int n = names.size();
    if (n > 0) {
      out.print("<table border=\"0\">\n");
      Iterator iter = names.iterator();
      for (int i = 0; i < n; i++) {
        String ni = (String) iter.next();
        out.print(
            "<tr><td align=\"right\">&nbsp;"+
            (i+1)+".&nbsp;</td><td align=\"right\">");
        int j = ni.indexOf('.');
        String head = 
          (j > 0 ? ni.substring(0, j) : j < 0 ? ni : null);
        if (head != null) {
          // looks like:  head(\.tail)?
          out.print("<a href=\"/$"+ni+"/list\">"+head+"</a>");
        }
        if (isLocal) {
          // possible different suffix per entry
          if (j > 0) {
            String tail = ni.substring(j);
            String links = createSuffixLinks(tail);
            out.print(links);
          }
        } else {
          if (j == 0) {
            // all have the same suffix: \.?mid(\.suffix)
            int k = ni.indexOf('.', 1);
            String mid = 
              (k > 0 ? ni.substring(0, k) : k < 0 ? ni : null);
            if (mid != null) {
              out.print(
                  "<a href=\"/agents?suffix="+
                  ni+"\">"+mid+"</a>");
            }
          }
          if (suffixLinks != null) {
            out.print(suffixLinks);
          }
        }
        out.print("</td></tr>\n");
      }
      out.print("</table>\n");
    } else {
      out.print("<font color=\"red\">zero agents found</font>");
    }
    out.print(
        "<p>\n"+
        "<a href=\"/agents\">Agents on host ("+
        serverName+":"+serverPort+
        ")</a><br>\n"+
        "<a href=\"/agents?suffix=.\""+
        ">Agents at the root (.)</a><br>"+
        "</body></html>\n");
  }

  /**
   * Given a suffix generate an HTML href list.
   * <p>
   * For example, given:<pre>
   *   .a.b.c
   * </pre>Generate:<pre>
   *   &lt;a href="/agents?suffix=.a.b.c"&gt;.a&lt;/a&gt; <i>+</i>
   *   &lt;a href="/agents?suffix=.b.c"&gt;.b&lt;/a&gt; <i>+</i>
   *   &lt;a href="/agents?suffix=.c"&gt;.c&lt;/a&gt;
   * </pre>
   */
  private static final String createSuffixLinks(String encSuffix) {
    // assert (encSuffix.charAt(0) == '.');
    StringBuffer buf = new StringBuffer();
    int len = encSuffix.length();
    for (int j = 0; j < len; ) {
      int k = encSuffix.indexOf('.', j+1);
      if (k < 0) {
        if (j >= len) {
          break;
        }
        k = len;
      }
      buf.append("<a href=\"/agents?suffix=");
      buf.append(encSuffix.substring(j));
      buf.append("\">");
      buf.append(encSuffix.substring(j,k));
      buf.append("</a>");
      j = k;
    }
    return buf.toString();
  }

  private static final void excludeRobots(
      HttpServletRequest req,
      HttpServletResponse res) throws ServletException, IOException {
    // we don't want web-crawlers!
    res.setContentType("text/plain");
    PrintWriter out = res.getWriter();
    out.print("User-agent: *\nDisallow: /\n");
    out.close();
  }

  private final void randomLocalRedirect(
      HttpServletRequest req,
      HttpServletResponse res,
      String trimPath) throws ServletException, IOException {

    // get the listing of all local names
    List localNames = localReg.listNames();
    int n = ((localNames != null) ? localNames.size() : 0);
    if (n <= 0) {
      res.sendError(
          HttpServletResponse.SC_NOT_FOUND, 
          "Zero local agents");
      return;
    }

    // select the random name
    int randIdx = rand.nextInt(n);
    String randName = (String) localNames.get(randIdx);

    String queryString = 
      req.getQueryString();

    // create the new location string
    String location = 
      "/$"+
      randName+
      trimPath+
      ((queryString != null) ? 
       ("?"+queryString) :
       (""));

    // encode for redirect -- typically a no-op
    location = res.encodeRedirectURL(location);

    // redirect the request to the remote location
    res.sendRedirect(location);
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
    return "random-local-redirect";
  }
  public void destroy() {
    // ignore
  }

}
