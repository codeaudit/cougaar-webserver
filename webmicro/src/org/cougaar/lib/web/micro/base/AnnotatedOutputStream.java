/*
 * <copyright>
 *  
 *  Copyright 1997-2007 BBNT Solutions, LLC
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

package org.cougaar.lib.web.micro.base;

import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletOutputStream;

/**
 * A marker class for a {@link ServletOutputStream} that contains an embedded
 * {@link #done} command.
 */
public abstract class AnnotatedOutputStream extends ServletOutputStream {

  /**
   * @return the output stream cast as an AnnotatedOutputStream, or trivially
   * wrapped if it is not an AnnotatedOutputStream
   */
  public static AnnotatedOutputStream toAnnotatedOutputStream(final OutputStream out) {
    if (out instanceof AnnotatedOutputStream || out == null) {
      return (AnnotatedOutputStream) out;
    }
    return new AnnotatedOutputStream() {
      private boolean done = false;
      public void write(int b) throws IOException { checkDone(); out.write(b); }
      public void write(byte b[]) throws IOException {
        checkDone(); out.write(b);
      }
      public void write(byte b[], int off, int len) throws IOException {
        checkDone(); out.write(b, off, len);
      }
      public void flush() throws IOException { checkDone(); out.flush(); }
      public void done() { checkDone(); done = true; }
      public void close() throws IOException { out.close(); }
      private void checkDone() {
        if (done) { throw new IllegalStateException("done"); }
      }
    };
  }

  /**
   * Mark the stream as "done" -- after this call, we're only allowed to call
   * {@link #close}.
   * <p>
   * When using sockets, we're not allowed to close the output stream before
   * we've finished using the input stream.  This is inefficient if we know
   * we've completed all our output.  The <code>done</code> method allows us to
   * tell the stream that we won't write any more data or call flush.  In
   * practice this may be handled just like <code>close</code>.
   */
  public abstract void done() throws IOException;

}
