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
package org.cougaar.lib.web.arch.util;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Simple wrapper for Servlets that implement
 * <code>SingleThreadModel</code> -- <b>note</b> that
 * if a Servlet is registered under multiple names then
 * this will only synchronize per <i>name</i>.
 */
public final class SynchronizedServlet implements Servlet {
  private final Servlet s;
  public SynchronizedServlet(Servlet s) {
    this.s = s;
  }
  // synchronize on service requests.
  public void service(
      ServletRequest req,
      ServletResponse res) throws ServletException, IOException {
    synchronized (this) {
      s.service(req, res);
    }
  }
  // forward the rest
  public void init(ServletConfig config) throws ServletException {
    s.init(config);
  }
  public ServletConfig getServletConfig() {
    return s.getServletConfig();
  }
  public String getServletInfo() {
    return s.getServletInfo();
  }
  public void destroy() {
    s.destroy();
  }
  public String toString() {
    return s.toString();
  }
}
