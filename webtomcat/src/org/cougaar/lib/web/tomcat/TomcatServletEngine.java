/*
 * <copyright>
 *  Copyright 2000-2001 BBNT Solutions, LLC
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
package org.cougaar.lib.web.tomcat;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.URL;
import javax.servlet.*;
import javax.servlet.http.*;

// will implement "ServletEngine":
import org.cougaar.lib.web.arch.server.*;

// using Tomcat 3.3:
import org.apache.tomcat.startup.EmbededTomcat;
import org.apache.tomcat.core.TomcatException;

/**
 * Implementation of <code>ServletEngine</code> for Tomcat 3.3.
 * <p>
 * The <tt>TomcatServletEngine(String[])</tt> constructor requires 
 * one argument:
 * <ol>
 *   </li>the full path to the Tomcat base directory</li>
 * </ol><br>
 * <p>
 * The directory must contain these files:
 * <ul>
 *   <li>conf/server.xml</li>
 *   <li>conf/modules.xml</li>
 *   <li>webapps/ROOT/WEB-INF/web.xml</li>
 *   <li>work/  <i>(read/write temporary directory)</i></li>
 * </ul>
 * <p>
 * Example files are in "webtomcat/data" and should not be 
 * modified.
 */
public class TomcatServletEngine 
  implements ServletEngine 
{

  private static final String[] CONFIG_FILES = {
    "conf/server.xml",
    "conf/modules.xml",
    "webapps/ROOT/WEB-INF/web.xml",
  };

  private final String installPath;

  private HttpConfig httpC;
  private HttpsConfig httpsC;
  private boolean debug;

  private boolean isRunning = false;

  public TomcatServletEngine(Object arg) {
    if (!(arg instanceof String)) {
      throw new IllegalArgumentException(
          "Tomcat ServletEngine expects a single String argument,"+
          " \"installPath\", not "+
          ((arg != null) ? arg.getClass().getName() : "null"));
    }
    String s = (String) arg;

    this.installPath = s;

    // verify the necessary config files
    verifyConfigFiles();
  }

  /**
   * Verify that the config files exist.
   * <p>
   * See the class-level notes for details.
   *
   * @throws RuntimeException if necessary files are missing
   */
  private void verifyConfigFiles() {
    // check the standard config files
    for (int i = 0; i < CONFIG_FILES.length; i++) {
      String s = installPath+"/"+CONFIG_FILES[i];
      File f = new File(s);
      if (!(f.exists())) {
        throw new RuntimeException(
            "Missing Tomcat config file: "+s);
      } else if (!(f.canRead())) {
        throw new RuntimeException(
            "Unable to read Tomcat config file: "+s);
      }
    }

    // check the "work" dir for read/write
    //
    // better to force this directory to exist than to let
    //   Tomcat attempt to create it and quietly fail
    String workS = installPath+"/work";
    File workDir = new File(workS);
    if (!(workDir.exists())) {
      throw new RuntimeException(
          "Missing Tomcat work directory: "+
          workS);
    } else if (!(workDir.isDirectory())) {
      throw new RuntimeException(
          "Tomcat work \"directory\" is not a directory: "+
          workS);
    } else if ((!(workDir.canRead())) ||
               (!(workDir.canWrite()))) {
      throw new RuntimeException(
          "Unable to open Tomcat work directory"+
          " for read/write access: "+
          workS);
    }

    // they all exist -- let Tomcat validate the contents
  }

  public void configure(
      HttpConfig httpC,
      HttpsConfig httpsC,
      boolean debug) {
    if (isRunning()) {
      throw new IllegalStateException(
          "Unable to configure a running Tomcat");
    }
    this.httpC = httpC;
    this.httpsC = httpsC;
    this.debug = debug;

    if (httpsC != null) {
      // Tomcat makes some restrictions here
      if (!("tomcat".equals(httpsC.getServerKeyname()))) {
        throw new IllegalArgumentException(
            "Tomcat ServletEngine requires the server"+
            " certificate's name to be \"tomcat\"");
      }
      if (httpsC.getClientAuth()) {
        String sk = httpsC.getServerKeystore();
        String tk = httpsC.getTrustKeystore();
        if (!(sk.equals(tk))) {
          throw new IllegalArgumentException(
              "Tomcat ServletEngine requires the server"+
              " keystore (\""+sk+"\") to also be the "+
              "trust keystore (\""+tk+"\")");
        }
      }
    }
  }

  public HttpConfig getHttpConfig() {
    return httpC;
  }

  public HttpsConfig getHttpsConfig() {
    return httpsC;
  }

  public boolean getDebug() {
    return debug;
  }

  public boolean isRunning() {
    return isRunning;
  }

  public void start() throws IOException {

    if (debug) {
      System.out.println("Start TomCat ServletEngine");
    }

    if (isRunning()) {
      throw new IllegalStateException(
          "Tomcat is currently running");
    }

    if ((httpC == null) &&
        (httpsC == null)) {
      throw new IllegalStateException(
          "Nothing to start -- both HTTP and HTTPS"+
          " configurations are null");
    }

    // launch tomcat with specified ports
    // tomcat automatically loads the HookServlet due to XML scripts
    try { 
      if (debug) {
        System.out.println("create embeded-tomcat");
      }
      EmbededTomcat et = new EmbededTomcat();

      if (debug) {
        System.out.println("turning up embeded-tomcat debug level");
        et.setDebug(30);
      }

      if (debug) {
        System.out.println("setting installPath: \""+installPath+"\"");
      }
      et.setInstall(installPath);

      if (debug) {
        System.out.println("setting class loaders");
      }
      ClassLoader cl = this.getClass().getClassLoader();
      if (cl == null) {
        cl = ClassLoader.getSystemClassLoader();
      }
      et.setParentClassLoader(cl);
      et.setCommonClassLoader(cl);
      et.setAppsClassLoader(cl);
      et.setContainerClassLoader(cl);

      if (httpC != null) {
        if (debug) {
          System.out.println("setting https endpoint: "+httpsC);
        }
        System.out.println(
            "Starting HTTP server on port: "+httpC.getPort());
        et.addEndpoint(
            httpC.getPort(),
            httpC.getAddress(), 
            httpC.getHostname());
      }

      if (httpsC != null) {
        // Tomcat 3.3's "addSecureEndpoint(..)" looks
        //   broken -- the keyfile and keypass are
        //   ignored (!).  Here we work around...
        if (debug) {
          System.out.println("setting https endpoint: "+httpsC);
        }
        HttpConfig shc = httpsC.getHttpConfig();
        System.out.println(
            "Starting HTTPS server on port: "+shc.getPort());
        int idx = 
          et.addSecureEndpoint( 
              shc.getPort(), 
              shc.getAddress(), 
              shc.getHostname(),
              null,
              null);
        et.setModuleProperty(
            idx, 
            "keystore", 
            httpsC.getServerKeystore());
	et.setModuleProperty(
            idx, 
            "keypass", 
            httpsC.getServerKeypass());
        if (httpsC.getClientAuth()) {
          et.setModuleProperty(
              idx, 
              "clientauth", 
              "true");
        }
      }

      if (debug) {
        System.out.println("setting args to {\"start\"}");
      }
      et.setArgs(new String[] {"start"});

      if (debug) {
        System.out.println("calling execute()");
      }
      et.execute();

      if (debug) {
        System.out.println("server is running");
      }

      this.isRunning = true;
    } catch (TomcatException te) {
      if (debug) {
        System.err.println(
            "Unable to start Tomcat: "+te.getMessage());
        te.printStackTrace();
      }
      String msg = te.getMessage();
      if ((msg != null) &&
          (msg.indexOf("Address already in use") >= 0)) {
        // very likely a port-in-use exception
        //
        // let the user figure out which port it was...
        throw new BindException(
            "Port already in use");
      }
      throw new RuntimeException(
          "Tomcat-internal exception: "+te.getMessage());
    } catch (Exception e) {
      if (debug) {
        System.err.println(
            "Unable to start Tomcat: "+e.getMessage());
        e.printStackTrace();
      }
      throw new RuntimeException(
          "Unknown Tomcat exception: "+e.getMessage());
    }
  }

  public void setGateway(Servlet servlet) throws ServletException {
    if (debug) {
      System.out.println("servlet engine setting gateway");
      System.out.println("  servlet: "+servlet);
      System.out.println("  hook: "+HookServlet.class);
      System.out.println("  classloader: "+
          HookServlet.class.getClassLoader());
      System.out.println("  stack: ");
      (new Exception()).printStackTrace();
    }
    HookServlet.setServlet(servlet);
  }

  public void stop() {
    if (!(isRunning())) {
      throw new IllegalStateException(
          "Tomcat not running");
    }

    try {
      setGateway(null);

      // see org.apache.share.startup.Tomcat's "stopTomcat()"
      System.out.println("Not implemented: Kill TomCat");
    } catch (Exception e) {
    }
     
    // discard configuration

    this.isRunning = false;
  }

  public String toString() {
    return "Tomcat ServletEngine";
  }
}
