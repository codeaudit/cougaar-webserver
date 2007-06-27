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
import java.io.InputStream;
import javax.servlet.ServletInputStream;

/**
 * A marker class for a {@link ServletInputStream} that contains embedded
 * "noop" and "flush" commands.
 * <p>
 * Our {@link ServletTunnel} needs this to preserve piped {@link 
 * java.io.InputStream#flush()} calls.  We use "noop"s to keep the stream
 * alive.
 * <p>
 * We use a "2" extension to avoid breaking the InputStream API contract.
 */
public abstract class AnnotatedInputStream extends ServletInputStream {

  public static final int NOOP = -3;
  public static final int FLUSH = -2;
  // "CLOSE" is effectively "-1"

  /**
   * @return the input stream cast as an AnnotatedInputStream, or trivially
   * wrapped if it is not an AnnotatedInputStream
   */
  public static AnnotatedInputStream toAnnotatedInputStream(final InputStream in) {
    if (in instanceof AnnotatedInputStream || in == null) {
      return (AnnotatedInputStream) in;
    }
    // wrap
    return new AnnotatedInputStream() {
      // use plain "read" methods for our "read2" impls: 
      public int read2() throws IOException {
        int ret = read();
        return (ret < 0 ? -1 : ret);
      }
      public int read2(byte[] b) throws IOException {
        int ret = read(b);
        return (ret < 0 ? -1 : ret);
      }
      public int read2(byte[] b, int off, int len) throws IOException {
        int ret = read(b, off, len);
        return (ret < 0 ? -1 : ret);
      }
      // forward the rest:
      public int read() throws IOException {
        return in.read();
      }
      public int read(byte[] b) throws IOException {
        return in.read(b);
      }
      public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
      }
      public long skip(long n) throws IOException {
        return in.skip(n);
      }
      public int available() throws IOException {
        return in.available();
      }
      public void close() throws IOException {
        in.close();
      }
      public void mark(int readlimit) {
        in.mark(readlimit);
      }
      public void reset() throws IOException {
        in.reset();
      }
      public boolean markSupported() {
        return in.markSupported();
      }
    };
  }

  /**
   * @return a NOOP if the caller should call this method again, a FLUSH if the
   * caller should flush their stream, otherwise a standard {@link #read()}
   * return value.
   */
  public abstract int read2() throws IOException;

  /** @see read2(byte[],int,int) */
  public int read2(byte[] b) throws IOException {
    return read2(b, 0, b.length);
  }

  /**
   * @return a NOOP if the caller should call this method again, a FLUSH if the
   * caller should flush their stream, otherwise a standard
   * {@link #read(byte[],int,int)} return value.
   * If the value is NOOP or FLUSH then zero bytes were written to the array.
   */
  public abstract int read2(byte[] b, int off, int len) throws IOException;

  //
  // backwards-compatibility:
  //

  /** Backwards-compatible {@link java.io.InputStream#read()} implementation */
  public int read() throws IOException {
    while (true) {
      int ret = read2();
      if (ret == NOOP || ret == FLUSH) continue;
      return ret;
    }
  }
  /** Backwards-compatible {@link java.io.InputStream#read(byte[])} implementation */
  public int read(byte b[]) throws IOException {
    return read(b, 0, b.length);
  }
  /** Backwards-compatible {@link java.io.InputStream#read(byte[],int,int)} implementation */
  public int read(byte[] b, int off, int len) throws IOException {
    while (true) {
      int count = read2(b, off, len);
      if (count == NOOP || count == FLUSH) continue;
      return count;
    }
  }
}
