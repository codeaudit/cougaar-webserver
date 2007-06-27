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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.thread.Schedulable;
import org.cougaar.lib.web.micro.base.AnnotatedOutputStream;

/**
 * An output stream pipe.
 * <p>
 * One thread writes to the "output_stream", and another thread (spawned by
 * this instance) periodically flushes the stream to our message-deliverer.
 */
public class OutputPipe {

  private final LoggingService log;
  private final Deliverer sender;
  private final Map metaData;
  private final long nagle;

  private final OutputStreamImpl out;

  private final Schedulable thread;

  // a list of buffered ByteArrayOutputStreams and Tokens
  private final List queue = new ArrayList();

  private boolean closed;

  private int counter = 0;

  /**
   * @param sender our output sender
   *
   * @param metaData optional meta-data to be sent in the first "deliver" call.
   *
   * @param nagle use -1 to send on every write/flush/close, 0 to only send on
   * the close, or a positive number to only send once that many milliseconds have
   * passed or a close has occured.
   */
  public OutputPipe(
      String threadName,
      LoggingService log,
      ThreadService threadService,
      Deliverer sender,
      Map metaData,
      long nagle) {
    this.log = log;
    this.sender = sender;
    this.metaData = metaData;
    this.nagle = nagle;
    this.out = new OutputStreamImpl();

    if (sender == null) {
      throw new IllegalArgumentException("null sender");
    }

    if (nagle == 0) {
      // only call the sender when we're done, in the "close()" thread
      thread = null;
    } else {
      Runnable r = new Runnable() {
        public void run() {
          checkQueue();
        }
      };
      // we may block up to our nagle duration
      thread = threadService.getThread(
          this, r, threadName, ThreadService.WILL_BLOCK_LANE);
    }
  }

  private void checkQueue() {
    if (log.isDebugEnabled()) {
      log.debug("checkQueue");
    }

    // take data off queue
    List data = null;
    synchronized (queue) {
      if (nagle < 0) {
        if (queue.isEmpty()) return;
        // take whatever's there, even if it's only a single byte
      } else {
        // wait a while, until either the nagle period expires or the stream
        // is closed (whichever comes first)
        //
        // TODO support min/max nagle and a periodic NOOP as a keep-alive
        if (!closed) {
          try {
            if (log.isDebugEnabled()) {
              log.debug("wait "+nagle);
            }
            queue.wait(nagle);
          } catch (InterruptedException e) {
          }
        }
        if (queue.isEmpty()) return;
      }
      data = new ArrayList(queue);
      queue.clear();
    }

    // convert baos's to byte[]s, to make them serializable
    for (int i = 0; i < data.size(); i++) {
      Object oi = data.get(i);
      if (oi instanceof ByteArrayOutputStream) {
        byte[] b = ((ByteArrayOutputStream) oi).toByteArray();
        data.set(i, b);
      }
    }

    // send
    sender.deliver(
        counter,
        (counter == 0 ? metaData : null),
        Collections.unmodifiableList(data));
    counter++;
  }

  /**
   * @return an output stream
   */
  public AnnotatedOutputStream getOutputStream() {
    return out;
  }

  public void close() {
    out.close();
  }

  private class OutputStreamImpl extends AnnotatedOutputStream {

    public void write(int b) {
      synchronized (queue) {
        getBuffer().write(b);
      }
      if (thread != null) {
        thread.start();
      }
    }

    public void write(byte[] b) {
      write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) {
      if (b == null) {
        throw new NullPointerException();
      } else if ((off < 0) || (off > b.length) || (len < 0) ||
          ((off + len) > b.length) || ((off + len) < 0)) {
        throw new IndexOutOfBoundsException();
      } else if (len == 0) {
        return;
      }

      synchronized (queue) {
        getBuffer().write(b, off, len);
      }
      if (thread != null) {
        thread.start();
      }
    }

    public void print(String s) {
      // this is optional.  The base class writes each individual character,
      // which is space-efficient but time-inefficient.
      write(s.getBytes());
    }

    private ByteArrayOutputStream getBuffer() {
      assert Thread.holdsLock(queue);
      if (closed) {
        throw new IllegalStateException("closed");
      }
      Object o = (queue.isEmpty() ? null : queue.get(queue.size() - 1));
      ByteArrayOutputStream buf;
      if (o instanceof ByteArrayOutputStream) {
        buf = (ByteArrayOutputStream) o;
      } else {
        buf = new ByteArrayOutputStream();
        queue.add(buf);
      }
      return buf;
    }

    public void flush() {
      synchronized (queue) {
        if (closed) return;
        Object last = (queue.isEmpty() ? null : queue.get(queue.size() - 1));
        if (last == Tokens.FLUSH) {
          // ignore second flush in a row
          return;
        }
        queue.add(Tokens.FLUSH);
      }
      if (thread != null) {
        thread.start();
      }
    }

    // since we're message-based, when we're done writing/flushing then
    // we should close the stream.
    //
    // This optimizes the tunnel by avoiding a separate, trivial, single-item
    // "close" message after the client has read our response.
    public void done() {
      close();
    }

    public void close() {
      List data = null;
      synchronized (queue) {
        if (closed) return;
        closed = true;
        queue.add(Tokens.CLOSE);
        if (thread == null) {
          data = new ArrayList(queue);
          queue.clear();
        } else {
          queue.notifyAll();
        }
      }
      if (thread == null) {
        sender.deliver(0, metaData, Collections.unmodifiableList(data)); 
      } else {
        thread.start();
      }
    }
  }
}
