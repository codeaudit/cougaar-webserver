/*
 * <copyright>
 *  
 *  Copyright 2000-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLConnection;
import java.util.Arrays;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.cougaar.bootstrap.SystemProperties;
import org.cougaar.core.servlet.ComponentServlet;

/**
 * A servlet that provides directory and file read access.
 * <p>
 * For example, to read file "$CIP/foo/bar.txt" on agent "A":<pre>
 *   http://localhost:8800/$A/file/foo/bar.txt
 * </pre>
 * <p>
 * We ignore most security issues.  Security can be added by not
 * loading this servlet or restricting the Java Security Policy.
 */
public class FileServlet extends ComponentServlet {

  private static final String BASE_PATH =
    SystemProperties.getProperty("org.cougaar.install.path");

  public void doGet(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    // get filename
    String filename = request.getPathInfo();
    if (filename != null) {
      filename = filename.trim();
      if (filename.length() == 0) {
        filename = null;
      }
    }
    if (filename == null) {
      filename = "/";
    }

    // trim leading and trailing "/"s
    //
    // Our servlet engine catches any bad paths, e.g. "//" and ".."
    boolean listFiles = filename.endsWith("/");
    if (listFiles) {
      filename = filename.substring(0, filename.length()-1);
    }
    if (filename.startsWith("/")) {
      filename = filename.substring(1);
    }

    if (listFiles) {
      // write directory listing
      File[] fa;
      try {
        File dir = locateFile(filename);
        fa = dir.listFiles();
      } catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      PrintWriter out = response.getWriter();
      String title = "Index of "+filename;
      out.println("<html>\n<head>\n<title>"+title+"</title>\n</head>\n<body>\n");
      out.println("<h1>"+title+"</h1>");
      out.print("<ul>");
      if (filename.length() > 0) {
        int sep = filename.lastIndexOf('/');
        String s = (sep < 0 ? "" : "/"+filename.substring(0, sep));
        out.println(
            "<li><a href=\""+request.getServletPath()+s+"/\">Parent Directory</a></li>");
      }
      
      Arrays.sort(fa);
      for (int i = 0; i < fa.length; i++) {
        File fi = fa[i];
        String pi = fi.getPath();
        int sep = pi.lastIndexOf(File.separator);
        pi = pi.substring(sep+1);
        if (fi.isDirectory()) {
          pi = pi + "/";
        }
        String fileServletPath = request.getServletPath() + 
          (filename.length() <= 0 ? "" : "/"+filename)+"/" + pi;
        
        out.println("<li><a href=\""+ fileServletPath +"\">"+pi+"</a></li>");
      }
      out.println("</ul>\n</body></html>");
      out.flush();
      return;
    }

    // open stream
    InputStream fin;
    try {
      fin = open(filename);
    } catch (IOException ioe) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // set content type
    String contentType = URLConnection.guessContentTypeFromStream(fin);
    if (contentType != null) {
      response.setContentType(contentType);
    }

    // maybe add "Expires" header?  See FavIconServlet for an example.

    // write data
    OutputStream out = response.getOutputStream();
    byte[] buf = new byte[2048];
    while (true) {
      int len = fin.read(buf);
      if (len < 0) {
        break;
      }
      out.write(buf, 0, len);
    }
    fin.close();
    out.flush();
    out.close();
  }

  // maybe add "doPut" for file upload/overwrite?

  private File locateFile(String filename) {
    return new File(BASE_PATH, filename);
    // return ConfigFinder.locateFile(filename);
  }
  private InputStream open(String filename) throws IOException {
    return new FileInputStream(new File(BASE_PATH, filename));
    //return ConfigFinder.open(filename);
  }
}
