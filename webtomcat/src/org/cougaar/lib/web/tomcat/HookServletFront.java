/*
 * <copyright>
 *  Copyright 1997-2002 Networks Associates Technology, Inc.
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
 *
 * Created on September 12, 2001, 10:55 AM
 */

package org.cougaar.lib.web.tomcat;

import java.io.IOException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * This class is designed to load 
 * org.cougaar.core.security.acl.auth.SecureHookServlet if it
 * exists and the System property
 * <code>org.cougaar.lib.web.tomcat.enableAuth</code> 
 * is "true".
 * <p>
 * The <code>web.xml</code> should have within the &lt;web-app&gt; element:
 * <pre>
 *   &lt;servlet&gt;
 *       &lt;servlet-name&gt;cougaar&lt;/servlet-name&gt;
 *       &lt;servlet-class&gt;org.cougaar.lib.web.tomcat.HookServletFront&lt;/servlet-class&gt;
 *   &lt;/servlet&gt;
 * </pre>
 * <p>
 * a privileged action under the principal of the user
 * who has logged in.
 *
 * @property org.cougaar.lib.web.tomcat.auth.class
 *   classname for hook servlet override class.
 * @property org.cougaar.lib.web.tomcat.enableAuth
 *   enable default hook class if the classname property
 *   is not specified.
 */
public class HookServletFront implements Servlet {

  private static final String PROP_ENABLE = 
    "org.cougaar.lib.web.tomcat.enableAuth";
  private static final String PROP_CLASS = 
    "org.cougaar.lib.web.tomcat.hookservlet.class";
  private static final String DEFAULT_SECURE =
    "org.cougaar.core.security.acl.auth.SecureHookServlet";

  private Servlet _hookServlet = null;
  
  /**
   * default constructor
   */
  public HookServletFront() {
    String servletClass = System.getProperty(PROP_CLASS);
    if (servletClass == null && Boolean.getBoolean(PROP_ENABLE)) {
      servletClass = DEFAULT_SECURE;
    }

    if (servletClass != null) {
      try {
        Class c = Class.forName(servletClass);
        _hookServlet = (Servlet) c.newInstance();
      } catch (ClassNotFoundException e) {
        System.err.println("Error: could not find class " + servletClass);
      } catch (ClassCastException e) {
        System.err.println("Error: the class " + servletClass + 
                           " is not a Servlet");
      } catch (Exception e) {
        System.err.println("Error: Could not load the class " + servletClass);
      }
    }
      
    if (_hookServlet == null) {
      _hookServlet = new HookServlet();
    }
  }

  /**
   * Call the hook servlet service
   */
  public void service(ServletRequest req, ServletResponse res) 
    throws ServletException, IOException {
    _hookServlet.service(req,res);
  }

  /**
   * Prepare the hook servlet to be destroyed.
   */
  public void destroy() {
    _hookServlet.destroy();
  }

  /**
   * Calls the Hook Servlet init()
   */
  public void init(ServletConfig config) throws ServletException {
    _hookServlet.init(config);
  }

  /**
   * Return the ServletConfig we got in init()
   */
  public ServletConfig getServletConfig() {
    return _hookServlet.getServletConfig();
  }

  /**
   * Returns the hook servlet's info, if available
   */
  public String getServletInfo() {
    return _hookServlet.getServletInfo();
  }
}
