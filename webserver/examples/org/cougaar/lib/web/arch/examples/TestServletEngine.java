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
package org.cougaar.lib.web.arch.examples;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.lib.web.arch.server.*;

/**
 * A utility class to test the <code>ServletEngine</code>.
 * <p>
 * When running all requests will display a "HELLO WORLD!" 
 * HTML page.
 * <p>
 * <pre>
 * @property org.cougaar.install.path
 *   Cougaar install path (required)
 * @property org.cougaar.lib.web.http.port
 *   HTTP port, defaults to 8800, set to -1 to disable
 * @property org.cougaar.lib.web.https.port
 *   HTTPS port, defaults to 8400, set to -1 to disable
 * @property org.cougaar.lib.web.https.clientAuth
 *   HTTPS clientAuth, defaults to "false"
 * @property org.cougaar.lib.web.https.keystore
 *   HTTPS certificate keystore, will prefix with install-path
 *   if it doesn't start with "/"
 * @property org.cougaar.lib.web.https.keypass
 *   HTTPS keystore password
 * </pre>
 */
public class TestServletEngine {

  public static void main(String[] args) throws Exception {

    // get the install path
    String cip = 
      System.getProperty("org.cougaar.install.path");
    if (cip == null) {
      System.err.println(
          "Requires \"-Dorg.cougaar.install.path=DIRECTORY\" to be set");
      return;
    }

    // from properties
    int httpPort = 
      Integer.getInteger("org.cougaar.lib.web.http.port", 8800).intValue();
    int httpsPort = 
      Integer.getInteger("org.cougaar.lib.web.https.port", -1).intValue();

    Map options = new HashMap(13);
    options.put("debug", "true");
    options.putAll(getHttpsOptions(httpsPort, cip));

    // create the server
    ServletEngine servEng =
      createServletEngine(cip);

    // create a simple servlet
    Servlet gatewayServlet = 
      new HttpServlet() {
        public void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
          throws IOException, ServletException {
            response.setContentType("text/html");
            response.getWriter().println(
                "<html><body><h1>HELLO WORLD!</h1></body></html>");
          }
        public String toString() {
          return "simple hello-world servlet";
        }
      };

    System.out.println("Servlet Engine: "+servEng);
    System.out.println("HTTP  port: "+httpPort);
    System.out.println("HTTPS port: "+httpsPort);
    System.out.println("Server options: "+options);
    System.out.println("Gateway Servlet: "+gatewayServlet);

    servEng.configure(
        httpPort,
        httpsPort,
        options);

    servEng.setGateway(gatewayServlet);

    servEng.start();

    System.out.println("Server running");
    while (true) {
      try {
        Thread.sleep(57000);
      } catch (Exception e) {
        break;
      }
    }
  }

  private static Map getHttpsOptions(
      int httpsPort, String cip) {
    Map httpsOptions;
    if (httpsPort <= 0) {
      // HTTPS disabled
      httpsOptions = Collections.EMPTY_MAP;
    } else {
      httpsOptions = new HashMap(13);
      // from properties here, would be from "cougaar.rc"
      String keystore =
        System.getProperty("org.cougaar.lib.web.https.keystore");
      if (keystore != null) {
        // keystore is relative to the "$org.cougaar.install.path"
        keystore = cip+"/"+keystore;
        httpsOptions.put("keystore", keystore);
      }
      String keypass =
        System.getProperty("org.cougaar.lib.web.https.keypass");
      if (keypass != null) {
        httpsOptions.put("keypass", keypass);
      }
      String sclientAuth =
        System.getProperty("org.cougaar.lib.web.https.clientAuth");
      if (sclientAuth != null) {
        httpsOptions.put("clientAuth", sclientAuth);
      }
    }
    return httpsOptions;
  }

  private static ServletEngine createServletEngine(
      String cip) throws Exception {
    // hard-code for Tomcat
    String engineClassname = 
      "org.cougaar.lib.web.tomcat.TomcatServletEngine";
    Object engineArg = cip+"/webtomcat/data";

    Class cl = Class.forName(engineClassname);
    Constructor cons = 
      cl.getConstructor(new Class[]{Object.class});
    Object o = cons.newInstance(new Object[]{engineArg});
    ServletEngine servEng = (ServletEngine) o;
    return servEng;
  }
}
