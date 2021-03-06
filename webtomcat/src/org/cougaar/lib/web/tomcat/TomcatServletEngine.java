/*
 * <copyright>
 *  
 *  Copyright 2000-2004 BBNT Solutions, LLC
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
package org.cougaar.lib.web.tomcat;

import java.io.File;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.jsp.JspFactory;

import org.apache.catalina.LifecycleException;
import org.cougaar.lib.web.engine.AbstractServletEngine;

/**
 * This component is a Tomcat 4.0.3 -based implementation of the
 * {@link org.cougaar.lib.web.engine.ServletEngineService}.
 * <p>
 * A "tomcat.path" parameter is required, which defaults to:<pre>
 *   $CIP/webtomcat/data
 * </pre>
 * This directory must contain these files:
 * <ul>
 *   <li>conf/server.xml</li>
 *   <li>conf/modules.xml</li>
 *   <li>webapps/ROOT/WEB-INF/web.xml</li>
 *   <li>work/  <i>(read/write temporary directory)</i></li>
 * </ul>
 * Where the above "/" directory separator matches the OS-specific
 * character defined in File.
 * <p>
 * Example files are in "webtomcat/data" and should not be modified.
 */
public class TomcatServletEngine extends AbstractServletEngine {

  private static final String SEP = File.separator;

  private static final String[] CONFIG_FILES = {
    "conf"+SEP+"server.xml",
    "conf"+SEP+"modules.xml",
    "webapps"+SEP+"ROOT"+SEP+"WEB-INF"+SEP+"web.xml",
  };

  private static final String JSP_FACTORY =
    "org.apache.jasper.runtime.JspFactoryImpl";

  private Map serverOptions;
  private int httpPort;
  private int httpsPort;
  private Map httpOptions;
  private Map httpsOptions;

  private EmbeddedTomcat et;

  /**
   * Verify that the config files exist.
   * <p>
   * See the class-level notes for details.
   *
   * @throws RuntimeException if necessary files are missing
   */
  private void verifyConfigFiles(String installPath) {
    // check args
    if (installPath == null) {
      throw new RuntimeException("null \"tomcat.path\"");
    }

    // check the standard config files
    for (int i = 0; i < CONFIG_FILES.length; i++) {
      String s = installPath+SEP+CONFIG_FILES[i];
      File f = new File(s);
      if (!(f.exists())) {
        throw new RuntimeException("Missing Tomcat config file: "+s);
      } else if (!(f.canRead())) {
        throw new RuntimeException("Unable to read Tomcat config file: "+s);
      }
    }

    // check the "work" dir for read/write
    //
    // better to force this directory to exist than to let
    //   Tomcat attempt to create it and quietly fail
    String workS = installPath+SEP+"work";
    File workDir = new File(workS);
    if (!(workDir.exists())) {
      throw new RuntimeException("Missing Tomcat work directory: "+workS);
    } else if (!(workDir.isDirectory())) {
      throw new RuntimeException(
          "Tomcat work \"directory\" is not a directory: "+workS);
    } else if ((!(workDir.canRead())) ||
               (!(workDir.canWrite()))) {
      throw new RuntimeException(
          "Unable to open Tomcat work directory for read/write access: "+
          workS);
    }

    // they all exist -- let Tomcat validate the contents
  }

  protected void configure(int httpPort, int httpsPort, Map options) {
    // save ports
    this.httpPort = httpPort;
    this.httpsPort = httpsPort;

    // extract the server options
    serverOptions = new HashMap(7);
    httpOptions = new HashMap(7);
    httpsOptions = new HashMap(7);
    for (Iterator iter = options.entrySet().iterator();
        iter.hasNext();
        ) {
      Map.Entry me = (Map.Entry) iter.next();
      String key = (String) me.getKey();
      String value = (String) me.getValue();
      if (key.indexOf(".") < 0) {
        serverOptions.put(key, value);
      } else if (key.startsWith("http.")) {
        httpOptions.put(key.substring(5), value);
      } else if (key.startsWith("https.")) {
        httpsOptions.put(key.substring(6), value);
      } else {
        // ignore?
      }
    }
    
    // set tomcat-specific defaults
    String installPath = (String) serverOptions.get("tomcat.path");
    if (installPath == null) {
      // backwards-compatible argument name
      installPath = (String) serverOptions.get("server.arg");
    }
    if (installPath == null) {
      // look for tomcat config files in RUNTIME, SOCIETY, then INSTALL
      for (int i = 0; i < 3; i++) {
        String s = (i == 0 ? "runtime" : i == 1 ? "society" : "install");
        String base = System.getProperty("org.cougaar."+s+".path");
        if (base != null) {
          String path = base+SEP+"webtomcat"+SEP+"data";
          if ((new File(path)).isDirectory()) {
            serverOptions.put("tomcat.path", path);
            break;
          }
        }
      }
    }
  }

  protected void startServer() throws BindException {
    // check ports
    if ((httpPort <= 0) && (httpsPort <= 0)) {
      throw new IllegalStateException(
          "Nothing to start -- both HTTP and HTTPS ports are negative");
    }

    // verify the necessary config files
    String installPath = (String) serverOptions.get("tomcat.path");
    verifyConfigFiles(installPath);

    // launch tomcat with specified ports
    // tomcat automatically loads the HookServlet due to XML scripts
    try { 

      // quick-check to see if the ports are free
      try {
        if (httpPort  > 0) {
          (new ServerSocket(httpPort)).close();
        }
        if (httpsPort > 0) {
          (new ServerSocket(httpsPort)).close();
        }
      } catch (SocketException se) {
        throw new LifecycleException(se);
      }

      // launch the server
      et = new EmbeddedTomcat();
      
      et.configure(serverOptions);

      et.setInstall(installPath);

      ClassLoader cl = getClass().getClassLoader();
      if (cl == null) {
        cl = ClassLoader.getSystemClassLoader();
      }

      et.setParentClassLoader(cl);
      try {
        et.readConfigFile(installPath+SEP+"conf"+SEP+"server.xml");
      } catch (Exception e) {
        throw new RuntimeException(
          "Tomcat-internal exception: " + e.getMessage());
      }
      
      if (httpPort > 0) {
        et.addEndpoint(httpPort, httpOptions);
      }

      if (httpsPort > 0) {
        et.addSecureEndpoint(httpsPort, httpsOptions);
      }

      et.embeddedStart();
    } catch (LifecycleException te) {
      Throwable teCause = te.getThrowable();
      if (teCause instanceof SocketException) {
        SocketException se = (SocketException) teCause;
        String msg = se.getMessage();
        if ((msg != null) &&
            ((msg.indexOf("Address already in use") >= 0) ||
             (msg.indexOf("Address in use") >= 0))) {
          // port is already in use
          if (se instanceof BindException) {
            throw (BindException) se;
          } else {
            throw new BindException(msg);
          }
        }
      }
      throw new RuntimeException("Tomcat-internal exception: ", te);
    } catch (Exception e) {
      throw new RuntimeException("Unknown Tomcat exception: ", e);
    }

    if (JspFactory.getDefaultFactory() == null) {
      try {
        Class jspFC = Class.forName(JSP_FACTORY);
        JspFactory jspF = (JspFactory) jspFC.newInstance();
        JspFactory.setDefaultFactory(jspF);
      } catch (Exception e) {
        // no "jasper-runtime", so no JSP support!
        //
        // we don't need to log this; if a JSP is constructed,
        // it'll throw an appropriate exception:
        //   java.lang.NoClassDefFoundError:
        //     org/apache/jasper/runtime/HttpJspBase
      }
    }
  }

  protected void setGateway(Servlet servlet) throws ServletException {
    HookServlet.setServlet(servlet);
  }

  protected void stopServer() {
    try {
      setGateway(null);

      et.embeddedStop();
      // Allow et to be garbage-collected.
      et = null;
    } catch (Exception e) {
      System.err.println("Error while shutting down Tomcat");
      e.printStackTrace();
    }
     
    // discard configuration
  }
}
