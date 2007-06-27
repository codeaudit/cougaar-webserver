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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.cougaar.core.agent.service.MessageSwitchService;
import org.cougaar.core.mts.Message;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.MessageHandler;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.thread.Schedulable;
import org.cougaar.core.util.UID;
import org.cougaar.lib.web.micro.base.AnnotatedInputStream;
import org.cougaar.lib.web.micro.base.AnnotatedOutputStream;
import org.cougaar.lib.web.micro.base.Connection;
import org.cougaar.lib.web.micro.base.ServerFactory;

/**
 * A connection factory for creating server accepts, such as to support
 * our {@link MessagingServletEngine}.
 */
public class MessagingServerFactory implements ServerFactory {

  /** A {@link PipeMessage} tag for messages we should receive */
  public static final String TYPE = "server";

  private final LoggingService log;
  private final ThreadService threadService;
  private final MessageSwitchService msgSwitch;
  private final long nagle;

  private AcceptCallback callback;

  // map from session UID to Pipe
  private final Map input_pipes = new HashMap();

  // todo for processing input messages
  private final TodoQueue todo;

  /**
   * @param nagle see {@link OutputPipe}
   */
  public MessagingServerFactory(
      LoggingService log,
      ThreadService threadService,
      MessageSwitchService msgSwitch,
      long nagle) {
    this.log = log;
    this.threadService = threadService;
    this.msgSwitch = msgSwitch;
    this.nagle = nagle;

    String s =
      (log == null ? "log" :
       threadService == null ? "threadService" :
       msgSwitch == null ? "msgSwitch" :
       null);
    if (s != null) {
      throw new IllegalArgumentException("null "+s);
    }

    // this thread won't block
    this.todo =
      new TodoQueue(
          log, threadService,
          "mts engine receiver",
          ThreadService.BEST_EFFORT_LANE) {
        protected void doNow(Object o) {
          handleMessage((PipeMessage) o);
        }
      };
  }

  public void start() {
    MessageHandler handler = new MessageHandler() {
      public boolean handleMessage(Message m) {
        if (!(m instanceof PipeMessage)) return false;
        PipeMessage pm = (PipeMessage) m;
        if (!TYPE.equals(pm.getType())) return false;
        // switch threads
        todo.add(pm);
        return true;
      }
    };
    msgSwitch.addMessageHandler(handler);
  }
  public void stop() {
    // no "msgSwitch.removeMessageHandler(handler)" method!
  }

  public ListenerControl listen(Map settings, AcceptCallback cb) {
    if (callback != null) {
      throw new IllegalStateException("Already have a callback");
    }

    this.callback = cb;
    return new ListenerControl() {
      public void stop() {
        if (callback != null) {
          callback = null;
        }
      }
    };
  }

  private void handleMessage(PipeMessage pm) {
    if (log.isDebugEnabled()) {
      log.debug("server-recv: "+pm);
    }

    if (callback == null) {
      // error?  no listener..
      if (log.isErrorEnabled()) {
        log.error("Unable to handle message, no callback listener!");
      }
      return;
    }

    final MessageAddress target = pm.getOriginator();
    Map metaData = pm.getMetaData();
    final UID sessionId = pm.getSessionId();
    int counter = pm.getCounter();

    List data = pm.getData();
    Object last = (data.isEmpty() ? null : data.get(data.size() - 1));
    boolean is_last = (last == Tokens.CLOSE);

    // create pipe or look it up if this is an existing connection
    final InputPipe ip;
    synchronized (input_pipes) {
      if (counter <= 0) {
        ip = new InputPipe();
        if (!is_last) {
          input_pipes.put(sessionId, ip);
        }
      } else {
        ip = (InputPipe) input_pipes.get(sessionId);
        if (is_last) {
          input_pipes.remove(sessionId);
        }
      }
    }
    if (ip == null) {
      if (log.isWarnEnabled()) {
        log.warn("Unknown sessionId specified by "+pm);
      }
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug("ip<"+sessionId+">.deliver("+counter+", ..");
    }
    ip.deliver(counter, metaData, data);
    if (counter > 0) {
      // just an input update, don't invoke new "accept" callback
      return;
    }

    if (log.isInfoEnabled()) {
      log.info("Handling servlet request from "+target+": "+pm);
    }

    Deliverer sender = new Deliverer() {
      public void deliver(int seq, Map meta, List dat) {
        PipeMessage pm =
          new PipeMessage(
              msgSwitch.getMessageAddress(), target,
              MessagingClientFactory.TYPE,
              sessionId, seq,
              meta, dat);
        // TODO specify message timeout equal to client's metaData read timeout
        if (log.isDebugEnabled()) {
          log.debug("server-send: "+pm);
        }
        msgSwitch.sendMessage(pm);
      }
    };
    final OutputPipe op = 
      new OutputPipe(
          "mts engine output pipe target="+target+" session="+sessionId,
          log, threadService, sender, null, nagle);

    final Connection con = new Connection() {
      public Map getMetaData() {
        return ip.getMetaData();
      }
      public AnnotatedInputStream getInputStream() throws IOException {
        return ip.getInputStream();
      }
      public AnnotatedOutputStream getOutputStream() throws IOException {
        return op.getOutputStream();
      }
      public void close() throws IOException {
        ip.close();
        op.close();
        synchronized (input_pipes) {
          input_pipes.remove(sessionId);
        }
      }
    };

    // run our "accept" callback in a separate thread, since servlets can block
    //
    // we want servlets to run in parallel threads
    Runnable r = new Runnable() {
      public void run() {
        try {
          if (log.isDebugEnabled()) {
            log.debug("accept con<"+sessionId+">");
          }
          callback.accept(con);
        } catch (Exception e) {
          if (log.isErrorEnabled()) {
            log.error("Accept failed for sessionId="+sessionId, e);
          }
        }
      }
    };
    Schedulable thread = 
      threadService.getThread(
          this, r,
          "mts engine servlet runner target="+target+" session="+sessionId,
          ThreadService.WILL_BLOCK_LANE);
    thread.start();
  }
}
