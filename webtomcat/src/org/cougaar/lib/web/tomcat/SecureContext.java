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

import java.lang.reflect.Method;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardContext;

/**
 * A Tomcat Context which will inform the security services
 * of its creation if the security services exist and the
 * System property
 * <code>org.cougaar.lib.web.tomcat.enableAuth</code> 
 * is "true".
 * <p>
 * @property org.cougaar.lib.web.tomcat.context.manager.class
 *   classname for manager of the context, which requires
 *   a "setContext(Context)" method.
 * @property org.cougaar.lib.web.tomcat.enableAuth 
 *   enable default context manager if classname property is
 *   not specified.
 */
public class SecureContext extends StandardContext {

  private static final String PROP_ENABLE =
    "org.cougaar.lib.web.tomcat.enableAuth";
  private static final String PROP_CLASS  =
    "org.cougaar.lib.web.tomcat.context.manager.class";
  private static final String DEFAULT_CONTEXT_MANAGER  = 
    "org.cougaar.core.security.provider.ServletPolicyServiceProvider";

  private Method _sppSetDualAuthenticator = null;

  public SecureContext() {
    String contextManagerClass = System.getProperty(PROP_CLASS);
    if (contextManagerClass == null && Boolean.getBoolean(PROP_ENABLE)) {
      contextManagerClass = DEFAULT_CONTEXT_MANAGER;
    }
    if (contextManagerClass != null) {
      try {
        Class c  = Class.forName(contextManagerClass);
        Method m = c.getMethod("setContext", new Class[] { Context.class });
        m.invoke(null, new Object[] { this });
      } catch (ClassNotFoundException e) {
        System.err.println("Error: Couldn't find " + contextManagerClass);
        // don't worry about it
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
