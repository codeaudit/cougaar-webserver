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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cougaar.core.agent.service.MessageSwitchService;
import org.cougaar.core.mts.Message;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.MessageHandler;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.util.UID;
import org.cougaar.lib.web.micro.base.AnnotatedInputStream;
import org.cougaar.lib.web.micro.base.AnnotatedOutputStream;
import org.cougaar.lib.web.micro.base.ClientFactory;
import org.cougaar.lib.web.micro.base.Connection;

/**
 * A connection factory for creating client requests, such as to support
 * our {@link MessagingServletTunnel}.
 */
public class MessagingClientFactory implements ClientFactory {

  /** A {@link PipeMessage} tag for messages we should receive */
  public static final String TYPE = "client";

  private final LoggingService log;
  private final UIDService uids;
  private final ThreadService threadService;
  private final MessageSwitchService msgSwitch;
  private final long nagle;

  // map from session UID to Pipe
  private final Map input_pipes = new HashMap();

  // todo for processing input messages
  private final TodoQueue todo;

  /**
   * @param nagle see {@link OutputPipe}
   */
  public MessagingClientFactory(
      LoggingService log,
      UIDService uids,
      ThreadService threadService,
      MessageSwitchService msgSwitch,
      long nagle) {
    this.log = log;
    this.uids = uids;
    this.threadService = threadService;
    this.msgSwitch = msgSwitch;
    this.nagle = nagle;

    String s = 
      (log == null ? "log" :
       uids == null ? "uids" :
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
          "mts tunnel receiver",
          ThreadService.BEST_EFFORT_LANE) {
        protected void doNow(Object o) {
          handleMessage((PipeMessage) o);
        }
      };
  }

  public void start() {
    MessageHandler handler = new MessageHandler() {
      // called in the mts thread
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

  // called in our "todo" thread
  private void handleMessage(PipeMessage pm) {
    if (log.isDebugEnabled()) {
      log.debug("client-recv: "+pm);
    }

    // lookup pipe
    UID sessionId = pm.getSessionId();

    List data = pm.getData();
    Object last = (data.isEmpty() ? null : data.get(data.size() - 1));
    boolean is_last = (last == Tokens.CLOSE);

    InputPipe ip;
    synchronized (input_pipes) {
      ip = (InputPipe) input_pipes.get(sessionId);
      if (is_last) {
        input_pipes.remove(sessionId);
      }
    }
    if (ip == null) {
      // unknown?  response to dead/closed pipe?
      if (log.isWarnEnabled()) {
        log.warn("Unknown sessionId specified by "+pm);
      }
      return;
    }

    // deliver to input pipe
    ip.deliver(pm.getCounter(), pm.getMetaData(), data);
  }

  public Connection connect(Object o, Map metaData) {
    if (!(o instanceof MessageAddress)) {
      throw new IllegalArgumentException(
          "Expecting a MessageAddress, not "+
          (o == null ? "null" : o.getClass().getName()));
    }
    MessageAddress address = (MessageAddress) o;

    return makeConnection(address, metaData);
  }

  private Connection makeConnection(
      final MessageAddress target, final Map metaData) {

    // create pipes
    final UID sessionId = uids.nextUID();
    final InputPipe ip = new InputPipe();
    synchronized (input_pipes) {
      input_pipes.put(sessionId, ip);
    }
    Deliverer sender = new Deliverer() {
      public void deliver(int counter, Map meta, List data) {
        PipeMessage pm =
          new PipeMessage(
              msgSwitch.getMessageAddress(), target,
              MessagingServerFactory.TYPE,
              sessionId, counter,
              meta, data);
        // TODO specify message timeout equal to server's metaData read timeout
        if (log.isDebugEnabled()) {
          log.debug("client-send: "+pm);
        }
        msgSwitch.sendMessage(pm);
      }
    };
    final OutputPipe op = 
      new OutputPipe(
          "mts tunnel target="+target+" session="+sessionId,
          log, threadService, sender,
          metaData, nagle);

    return new Connection() {
      public Map getMetaData() {
        return null; // not applicable
      }
      public AnnotatedInputStream getInputStream() {
        return ip.getInputStream();
      }
      public AnnotatedOutputStream getOutputStream() {
        return op.getOutputStream();
      }
      public void close() {
        ip.close();
        op.close();
        synchronized (input_pipes) {
          input_pipes.remove(sessionId);
        }
      }
    };
  }
}
