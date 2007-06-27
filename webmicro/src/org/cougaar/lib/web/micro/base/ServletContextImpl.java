/*
 * <copyright>
 *  
 *  Copyright 1997-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.micro.base;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;

/**
 * A minimal servlet context implementation.
 * <p>
 * In the currently version, nearly all of the methods are not supported.
 */
public class ServletContextImpl implements ServletContext {

  private static final Enumeration EMPTY_ENUMERATION =
    new Enumeration() {
      public boolean hasMoreElements() { return false; }
      public Object nextElement() { return null; }
    };

  // engine id 
  public String getServerInfo() { return "Cougaar Micro/1.0"; }

  // disable logging
  public void log(String msg) {}
  public void log(String message, Throwable throwable) {}

  // the rest is disabled:
  public ServletContext getContext(String uripath) { return null; }
  public int getMajorVersion() { return 2; }
  public int getMinorVersion() { return 3; }
  public String getMimeType(String file) { return null; }
  public Set getResourcePaths(String path) { return null; }
  public URL getResource(String path) { return null; }
  public InputStream getResourceAsStream(String path) { return null; }
  public RequestDispatcher getRequestDispatcher(String path) { die(); return null; }
  public RequestDispatcher getNamedDispatcher(String name) { die(); return null; }
  public Servlet getServlet(String name) { die(); return null; }
  public Enumeration getServlets() { return EMPTY_ENUMERATION; }
  public Enumeration getServletNames() { return EMPTY_ENUMERATION; }
  public void log(Exception exception, String msg) { log(msg, exception); }
  public String getRealPath(String path) { die(); return null; }
  public String getInitParameter(String name) { return null; }
  public Enumeration getInitParameterNames() { return EMPTY_ENUMERATION; }
  public Object getAttribute(String name) { return null; }
  public Enumeration getAttributeNames() { return EMPTY_ENUMERATION; }
  public void setAttribute(String name, Object object) { if (object != null) die(); }
  public void removeAttribute(String name) {}
  public String getServletContextName() { return null; }

  private static final void die() { throw new UnsupportedOperationException("die"); }
}
