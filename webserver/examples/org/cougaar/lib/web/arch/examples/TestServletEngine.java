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
package org.cougaar.lib.web.arch.examples;

import java.io.IOException;
import java.net.InetAddress;
import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.lib.web.arch.server.*;

/**
 * Utility to test <code>ServletEngine</code>.
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

    // hard-code for Tomcat
    String engineClassname = 
      "org.cougaar.lib.web.tomcat.TomcatServletEngine";
    Object engineArg = cip+"/webtomcat/data";

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

    // from properties
    int httpPort = 
      Integer.getInteger("org.cougaar.lib.web.http.port", 8800).intValue();
    int httpsPort = 
      Integer.getInteger("org.cougaar.lib.web.https.port", 8400).intValue();
    boolean clientAuth;

    String serverKeystore;
    String serverKeypass;
    if (httpsPort <= 0) {
      // HTTPS disabled
      clientAuth = false;
      serverKeystore = null;
      serverKeypass = null;
    } else {
      // from properties here, would be from "cougaar.rc"
      serverKeystore = 
        System.getProperty("org.cougaar.lib.web.https.keystore");
      serverKeypass =
        System.getProperty("org.cougaar.lib.web.https.keypass");
      clientAuth = 
        Boolean.getBoolean("org.cougaar.lib.web.https.clientAuth");
      if ((serverKeystore == null) ||
          (serverKeypass == null)) {
        System.err.println(
            "Must set both \"org.cougaar.lib.web.https.keystore\""+
            " and \"org.cougaar.lib.web.https.keypass\"");
        return;
      }
      // keystore is relative to the "$org.cougaar.install.path"
      serverKeystore = cip+"/"+serverKeystore;
    }

    InetAddress addr = InetAddress.getByName("localhost");

    // run the test
    test(
        engineClassname, 
        engineArg, 
        addr,
        httpPort,
        httpsPort,
        serverKeystore,
        serverKeypass,
        clientAuth,
        gatewayServlet);
  }

  public static void test(
      String engineClassname,
      Object engineArg,
      InetAddress addr,
      int httpPort,
      int httpsPort,
      String serverKeystore,
      String serverKeypass,
      boolean clientAuth,
      Servlet gatewayServlet) throws Exception {

    HttpConfig httpC = 
      ((httpPort > 0) ?
       (new HttpConfig(addr, httpPort)) :
       (null));

    HttpsConfig httpsC =
      ((httpsPort > 0) ?
       (new HttpsConfig(
          new HttpConfig(addr, httpsPort),
          clientAuth,
          serverKeystore,
          serverKeypass,
          "tomcat",
          serverKeystore)) :
       (null));

    test(engineClassname, engineArg, httpC, httpsC, gatewayServlet);
  }

  public static void test(
      String engineClassname,
      Object engineArg,
      HttpConfig httpC,
      HttpsConfig httpsC,
      Servlet gatewayServlet) throws Exception {

    Class cl = Class.forName(engineClassname);
    java.lang.reflect.Constructor cons = 
      cl.getConstructor(new Class[]{Object.class});
    Object o = cons.newInstance(new Object[]{engineArg});
    ServletEngine servEng = (ServletEngine) o;

    test(servEng, httpC, httpsC, gatewayServlet);
  }

  public static void test(
      ServletEngine servEng,
      HttpConfig httpC,
      HttpsConfig httpsC,
      Servlet gatewayServlet) throws Exception {

    System.out.println("Servlet Engine: "+servEng);
    System.out.println("HTTP config: "+httpC);
    System.out.println("HTTPS config: "+httpsC);
    System.out.println("Gateway Servlet: "+gatewayServlet);

    servEng.configure(
        httpC,
        httpsC,
        true); // turn on full debugging with "true"

    servEng.setGateway(gatewayServlet);

    servEng.start();

    System.out.println("Server running");
  }
}
