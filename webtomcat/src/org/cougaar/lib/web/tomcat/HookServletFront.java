/*
 * <copyright>
 *  
 *  Copyright 1997-2004 Networks Associates Technology, Inc
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
 *
 * Created on September 12, 2001, 10:55 AM
 */

package org.cougaar.lib.web.tomcat;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
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
