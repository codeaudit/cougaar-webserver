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

import java.lang.reflect.Method;

import org.apache.catalina.Context;
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
