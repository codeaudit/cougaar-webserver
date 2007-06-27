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

package org.cougaar.lib.web.micro.mts;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cougaar.lib.web.micro.base.AnnotatedInputStream;

/**
 * An input stream pipe.
 * <p>
 * One thread calls "deliver" to add to the pipe, and another thread
 * reads from the "input_stream".
 */
public class InputPipe implements Deliverer {

  // a list of buffered byte[]s and Tokens
  private final LinkedList queue = new LinkedList();

  private final InputStreamImpl in;

  private Map metaData;
  private int counter = -1;
  private boolean in_closed;
  private boolean out_closed;

  public InputPipe() {
    in = new InputStreamImpl();
  }

  /** Non-blocking deliver call */
  public void deliver(int counter, Map metaData, List data) {
    synchronized (queue) {
      // check if closed
      if (in_closed) {
        // our local client closed the input stream (?)
        return;
      }

      // update counter
      if (counter != this.counter + 1) {
        throw new RuntimeException(
            "Unexpected counter value "+counter+
            ", expecting "+(this.counter+1));
      }
      this.counter = counter;

      if (counter == 0) {
        // save metadata
        this.metaData = (metaData == null ? Collections.EMPTY_MAP : metaData);
      }

      // append to queue
      queue.addAll(data);
      queue.notifyAll();
    }
  }

  public Map getMetaData() {
    synchronized (queue) {
      while (metaData == null && (!in_closed && !out_closed)) {
        try {
          // TODO have max-wait limit for stream timeout
          queue.wait();
        } catch (InterruptedException ie) {
          throw new RuntimeException("interrupted");
        }
      }
      return metaData;
    }
  }

  public AnnotatedInputStream getInputStream() {
    return in;
  }

  public void close() {
    in.close();
  }

  private class InputStreamImpl extends AnnotatedInputStream {
    // our current byte[], removed from the head of the list
    private byte[] buf = null;
    private int offset = 0;

    // for "int read2()"
    private byte[] tmp = new byte[1];

    public int read2() {
      synchronized (queue) {
        while (true) {
          int count = _read2(tmp, 0, 1);
          switch (count) {
            case 1: return tmp[0];
            case 0: break;
            case -1: return -1;
            case NOOP: return NOOP;
            case FLUSH: return FLUSH;
            default: throw new RuntimeException("Invalid read count: "+count);
          }
        }
      }
    }

    public int read2(byte b[]) {
      return read2(b, 0, b.length);
    }

    public int read2(byte[] b, int off, int len) {
      if (b == null) {
        throw new NullPointerException();
      } else if ((off < 0) || (off > b.length) || (len < 0) ||
          ((off + len) > b.length) || ((off + len) < 0)) {
        throw new IndexOutOfBoundsException();
      } else if (len == 0) {
        return 0;
      }
      synchronized (queue) {
        return _read2(b, off, len);
      }
    }

    private int _read2(byte[] b, int off, int len) {
      assert Thread.holdsLock(queue);

      if (buf == null) {
        while (true) {
          if (out_closed || in_closed) return -1;
          if (!queue.isEmpty()) break;
          try {
            // TODO have max-wait limit for stream timeout
            queue.wait();
          } catch (InterruptedException ie) {
            throw new RuntimeException("interrupted");
          }
        }
        Object o = queue.removeFirst();
        if (o instanceof ByteArrayOutputStream) {
          // our InputPipe is supposed to convert these, but we'll
          // catch this case regardless
          o = ((ByteArrayOutputStream) o).toByteArray();
        }
        if (o instanceof byte[]) {
          buf = (byte[]) o;
          offset = 0;
        } else if (o == Tokens.CLOSE) {
          out_closed = true;
          return -1;
        } else if (o == Tokens.FLUSH) {
          return FLUSH;
        } else if (o == Tokens.NOOP) {
          return NOOP;
        } else {
          throw new RuntimeException(
              "Unexpected type: "+
              (o == null ? "null" : o.getClass().getName()));
        }
      }

      int n = Math.min(buf.length - offset, len);
      System.arraycopy(buf, offset, b, off, n);
      offset += n;
      if (offset == buf.length) {
        buf = null;
        offset = 0;
      }
      return n;
    }

    public void close() {
      synchronized (queue) {
        if (in_closed) return;
        in_closed = true;
        queue.clear();
        buf = null;
        offset = 0;
        queue.notifyAll();
      }
    }
  }
}
