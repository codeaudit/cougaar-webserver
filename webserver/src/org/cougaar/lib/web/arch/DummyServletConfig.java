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
package org.cougaar.lib.web.arch;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import javax.servlet.*;

/**
 * Dummy <code>ServletConfig</code> used in "Servlet.init(..)".
 * <p>
 * Cougaar Servlets should not depend upon the ServletConfig!
 * This class is provided for backwards compatibility of existing
 * Servlets.  If later it turns out that some ServletConfig
 * information is needed then it will be added.
 *
 * @see ServletConfig
 */
public final class DummyServletConfig 
implements ServletConfig {

  /**
   * Get an instance of the dummy <code>ServletConfig</code>.
   */
  public static DummyServletConfig getInstance() {
    return DUMMY_SERVLET_CONFIG;
  }


  private static final DummyServletConfig DUMMY_SERVLET_CONFIG = 
    new DummyServletConfig();

  private static final DummyServletContext DUMMY_SERVLET_CONTEXT = 
    new DummyServletContext();

  private static final Enumeration EMPTY_ENUMERATION = 
    new Enumeration() {
      public boolean hasMoreElements() {
        return false;
      }
      public Object nextElement() {
        throw new java.util.NoSuchElementException();
      }
    };

  public ServletContext getServletContext() {
    return DUMMY_SERVLET_CONTEXT;
  }

  public String getInitParameter(String name) {
    return null;
  }

  public Enumeration getInitParameterNames() {
    return EMPTY_ENUMERATION;
  }

  public String getServletName() {
    return "no-servlet-name";
  }

  private static class DummyServletContext  // ignore deprecation warnings!
  implements ServletContext 
  {
    public java.util.Set getResourcePaths(String s) {
      return java.util.Collections.EMPTY_SET;
    }
    public String getServletContextName() {
      return "Cougaar";
    }
    public ServletContext getContext(String uripath) {
      return null;
    }
    public int getMajorVersion() {
      return 2;
    }
    public int getMinorVersion() {
      return 2;
    }
    public String getMimeType(String file) {
      return null;
    }
    public URL getResource(String path) {
      return null;
    }
    public InputStream getResourceAsStream(String path) {
      // use the ConfigFinder!
      return null;
    }
    public RequestDispatcher getRequestDispatcher(String path) {
      return null;
    }
    public RequestDispatcher getNamedDispatcher(String name) {
      return null;
    }
    /** @deprecated ignore this warning */
    public Servlet getServlet(String name) {
      return null;
    }
    /** @deprecated ignore this warning */
    public Enumeration getServlets() {
      return EMPTY_ENUMERATION;
    }
    /** @deprecated ignore this warning */
    public Enumeration getServletNames() {
      return EMPTY_ENUMERATION;
    }
    public void log(String msg) {
      // use the LoggingService!
    }
    /** @deprecated ignore this warning */
    public void log(Exception exception, String msg) {
    }
    public void log(String message, Throwable throwable) {
    }
    public String getRealPath(String path) {
      return null;
    }
    public String getServerInfo() {
      return "Cougaar";
    }
    public String getInitParameter(String name) {
      return null;
    }
    public Enumeration getInitParameterNames() {
      return EMPTY_ENUMERATION;
    }
    public Object getAttribute(String name) {
      return null;
    }
    public Enumeration getAttributeNames() {
      return EMPTY_ENUMERATION;
    }
    public void setAttribute(String name, Object object) {
      // throw new UnsupportedOperationException();
    }
    public void removeAttribute(String name) {
    }
  }
}
