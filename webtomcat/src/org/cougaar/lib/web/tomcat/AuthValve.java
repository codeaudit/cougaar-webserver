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
 * CHANGE RECORD
 * - 
 */
package org.cougaar.lib.web.tomcat;

import java.io.IOException;
import java.lang.reflect.Method;

import javax.servlet.ServletException;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.Request;
import org.apache.catalina.Response;
import org.apache.catalina.Valve;
import org.apache.catalina.ValveContext;
import org.apache.catalina.valves.ValveBase;

/**
 * A Valve for Tomcat 4.0 that will use
 * org.cougaar.core.security.acl.auth.DualAuthenticator if it
 * exists and the System property
 * <code>org.cougaar.lib.web.tomcat.enableAuth</code> 
 * is "true".
 * <p>
 * The <code>server.xml</code> should have within the &lt;Context&gt; section:
 * <pre>
 *   &lt;Valve className="org.cougaar.lib.web.tomcat.AuthValve" realmName="Cougaar" authMethod="DIGEST"/&gt;
 * </pre>
 * <p>
 * Where realmName is the realm to use for authenticating users and
 * authMethod is the secondary method for authentication. It accepts any of the
 * standard methods available for authentication in servlet 2.3 spec.
 * Remember that the primary authentication is always CERT, so don't use that.
 *
 *
 * @property org.cougaar.lib.web.tomcat.auth.class
 *   classname for authenticator.
 * @property org.cougaar.lib.web.tomcat.enableAuth
 *   enable default authenticator class if the classname property
 *   is not specified.
 */
public class AuthValve implements Valve, Contained {

  private static final String PROP_ENABLE =
    "org.cougaar.lib.web.tomcat.enableAuth";
  private static final String PROP_CLASS =
    "org.cougaar.lib.web.tomcat.auth.class";
  private static final String DEFAULT_SECURE = 
    "org.cougaar.core.security.acl.auth.DualAuthenticator";

  private ValveBase _authValve = null;
  private Container _container = null;

  /** 
   * Default constructor.
   */
  public AuthValve() {
    String authClass = System.getProperty(PROP_CLASS);
    if (authClass == null && Boolean.getBoolean(PROP_ENABLE)) {
      authClass = DEFAULT_SECURE;
    }
    if (authClass != null) {
      try {
        Class c = Class.forName(authClass);
        _authValve = (ValveBase) c.newInstance();
      } catch (ClassNotFoundException e) {
        System.err.println("Error: could not find class " + authClass);
      } catch (ClassCastException e) {
        System.err.println("Error: the class " + authClass +
                           " is not a Valve");
      } catch (Exception e) {
        System.err.println("Error: could not load the class " + authClass);
      }
    }
  }

  /**
   * returns the DualAuthenticator Valve if it is available.
   */
  public ValveBase getValve() {
    return _authValve;
  }

  /**
   * returns the DualAuthenticator getInfo() if available or "AuthValve"
   * otherwise
   */
  public String getInfo() {
    if (_authValve != null) {
      return _authValve.getInfo();
    }
    return "AuthValve";
  }

  /**
   * Calls DualAuthenticator invoke() if available or calls
   * context.invokeNext() if not.
   */
  public void invoke(Request request, Response response, ValveContext context) 
    throws IOException, ServletException {
    if (_authValve != null) {
      _authValve.invoke(request, response, context);
    } else {
      context.invokeNext(request, response);
    }
  }

  /**
   * Calls DualAuthenticator setRealmName() if available
   */
  public void setRealmName(String realmName) {
    if (_authValve != null) {
      try {
        Method m = _authValve.getClass().
          getMethod("setRealmName", new Class[] { String.class });
        m.invoke( _authValve, new Object[] { realmName } );
      } catch (Exception e) {
        e.printStackTrace();
      }
    } 
  }

  /**
   * Returns DualAuthenticator getRealmName() if available or
   * <code>null</code> otherwise.
   */
  public String getRealmName() {
    if (_authValve != null) {
      try {
        Method m = _authValve.getClass().getMethod("getRealmName", null);
        return (String) m.invoke( _authValve, null );
      } catch (Exception e) {
        // don't worry about it.
      }
    } 
    return null;
  }

  /**
   * Calls DualAuthenticator setAuthMethod() if available
   */
  public void setAuthMethod(String authMethod) {
    if (_authValve != null) {
      try {
        Method m = _authValve.getClass().
          getMethod("setAuthMethod", new Class[] { String.class });
        m.invoke( _authValve, new Object[] { authMethod } );
      } catch (Exception e) {
        // don't worry about it.
      }
    } 
  }

  /**
   * Returns DualAuthenticator getAuthMethod() if available or
   * <code>null</code> otherwise.
   */
  public String getAuthMethod() {
    if (_authValve != null) {
      try {
        Method m = _authValve.getClass().getMethod("getAuthMethod", null);
        return (String) m.invoke( _authValve, null );
      } catch (Exception e) {
        // don't worry about it.
      }
    } 
    return null;
  }

  /**
   * Sets the context container
   */
  public void setContainer(Container container) {
    if (_authValve != null) {
      _authValve.setContainer(container);
    } 
    _container = container;
  }

  /**
   * Returns the context container
   */
  public Container getContainer() {
    if (_authValve != null) {
      return _authValve.getContainer();
    }
    return _container;
  }
}
