/*
 * <copyright>
 *  Copyright 1997-2003 Networks Associates Technology, Inc
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
 * CHANGE RECORD
 * - 
 */
package org.cougaar.lib.web.tomcat;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.beans.PropertyChangeListener;

import org.apache.catalina.Container;
import org.apache.catalina.Realm;

/**
 * A Realm extension for Tomcat 4.0 that will use
 * org.cougaar.core.security.crypto.ldap.KeyRingJNDIRealm if it
 * exists and the System property
 * <code>org.cougaar.lib.web.tomcat.enableAuth</code> 
 * is "true".
 * <p>
 * The <code>server.xml</code> should have within the &lt;Engine&gt; section:
 * <pre>
 *   &lt;Realm className="org.cougaar.lib.web.tomcat.SecureRealm" /&gt;
 * </pre>
 *
 * @author George Mount <gmount@nai.com>
 *
 * @property org.cougaar.lib.web.tomcat.realm.class
 *   classname for realm.
 * @property org.cougaar.lib.web.tomcat.enableAuth 
 *   enable default realm if classname property is
 *   not specified.
 */
public class SecureRealm implements Realm {

  private static final String PROP_ENABLE = 
    "org.cougaar.lib.web.tomcat.enableAuth";
  private static final String PROP_CLASS  =
    "org.cougaar.lib.web.tomcat.realm.class";
  private static final String DEFAULT_SECURE =
    "org.cougaar.core.security.crypto.ldap.KeyRingJNDIRealm";

  private Realm _secureRealm = null;
  private Container _container = null;

  /** 
   * Default constructor.
   */
  public SecureRealm() {
    String realmClass = System.getProperty(PROP_CLASS);

    if (realmClass == null && Boolean.getBoolean(PROP_ENABLE)) {
      realmClass = DEFAULT_SECURE;
    }

    if (realmClass != null) {
      try {
        Class c = Class.forName(realmClass);
        _secureRealm = (Realm) c.newInstance();
      } catch (ClassNotFoundException e) {
        System.err.println("Error: could not find class " + realmClass);
      } catch (ClassCastException e) {
        System.err.println("Error: the class " + realmClass +
                           " is not a Realm");
      } catch (Exception e) {
        System.err.println("Error: could not load the class " + realmClass);
      }
    }
  }

  /**
   * returns the KeyRingJNDIRealm if it is available.
   */
  public Realm getRealm() {
    return _secureRealm;
  }

  /** 
   * Uses the KeyRingJNDIRealm's addPropertyChangeListener if available.
   */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    if (_secureRealm != null) {
      _secureRealm.addPropertyChangeListener(listener);
    }
  }

  /**
   * Authenticates using the KeyRingJNDIRealm if available.
   */
  public Principal authenticate(String username, String credentials) {
    if (_secureRealm != null) {
      return _secureRealm.authenticate(username, credentials);
    }
    return null;
  }

  /**
   * Authenticates using the KeyRingJNDIRealm if available.
   */
  public Principal authenticate(String username, byte[] credentials) {
    if (_secureRealm != null) {
      return _secureRealm.authenticate(username, credentials);
    }
    return null;
  }

  /**
   * Authenticates using the KeyRingJNDIRealm if available.
   */
  public Principal authenticate(X509Certificate certs[]) {
    if (_secureRealm != null) {
      return _secureRealm.authenticate(certs);
    }
    return null;
  }

  /**
   * Authenticates using the KeyRingJNDIRealm if available.
   */
  public Principal authenticate(String username, String clientDigest,
                                String nOnce, String nc, String cnonce,
                                String qop, String realm,
                                String md5a2) {
    if (_secureRealm != null) {
      return _secureRealm.authenticate(username, clientDigest,
                                       nOnce, nc, cnonce, qop, realm, md5a2);
    }
    return null;
  }

  /**
   * Uses the KeyRingJNDIRealm getContainer() if available
   */
  public Container getContainer() {
    if (_secureRealm != null) {
      return _secureRealm.getContainer();
    }
    return _container;
  }
    
  /**
   * Uses the KeyRingJNDIRealm getInfo() if available. Otherwise it returns
   * "SecureRealm";
   */
  public String getInfo() {
    if (_secureRealm != null) {
      return _secureRealm.getInfo();
    }
    return "SecureRealm";
  }
    
  /**
   * Uses the KeyRingJNDIRealm hasRole() if available. Otherwise it returns
   * false always
   */
  public boolean hasRole(Principal user, String role) {
    if (_secureRealm != null) {
      return _secureRealm.hasRole(user, role);
    }
    return false;
  }

  /**
   * Uses the KeyRingJNDIRealm removePropertyChangeListener() if available.
   */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    if (_secureRealm != null) {
      _secureRealm.removePropertyChangeListener(listener);
    }
  }

  /**
   * Uses the KeyRingJNDIRealm setContainer() if available. Otherwise it
   * sets the value to be returned by getContainer()
   */
  public void setContainer(Container container) {
    if (_secureRealm != null) {
      _secureRealm.setContainer(container);
    }
    _container = container;
  }
}
