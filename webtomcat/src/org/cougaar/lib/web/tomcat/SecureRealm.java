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

import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.security.cert.X509Certificate;

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
