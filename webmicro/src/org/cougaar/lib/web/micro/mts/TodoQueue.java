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

import java.util.ArrayList;
import java.util.List;

import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.thread.Schedulable;

/**
 * A simple "todo" queue for processing work in a pooled thread.
 * <p>
 * Should repackage into <code>org.cougaar.core.util</code>.<br>
 * The blackboard equivalent should use a dummy subscription to hold
 * onto queued items and present a "getAddedList()" interface.
 */
public abstract class TodoQueue {

  private final LoggingService log;
  private final Schedulable thread;

  private final List todo = new ArrayList();
  private final List tmp = new ArrayList();

  /**
   * @param lane ThreadService lane, e.g. WILL_BLOCK_LANE
   */
  public TodoQueue(
      LoggingService log, ThreadService threadService,
      String threadName, int lane) {
    this.log = log;

    Runnable r = new Runnable() {
      public void run() {
        doAllNow();
      }
    };
    this.thread = threadService.getThread(this, r, threadName, lane);
  }

  /**
   * Add work to do in our asynchronous "doNow" callback.
   *
   * @param o any object, such as a Runnable
   */
  public void add(Object o) {
    synchronized (todo) {
      todo.add(o);
    }
    thread.start();
  }

  /**
   * Do previously queued work in our single-threaded pooled thread.
   *
   * @param o an object passed into {@link #add(Object)}.
   */
  protected abstract void doNow(Object o);

  private void doAllNow() {
    synchronized (todo) {
      if (todo.isEmpty()) return;
      tmp.addAll(todo);
      todo.clear();
    }
    for (int i = 0, n = tmp.size(); i < n; i++) {
      Object oi = tmp.get(i);
      try {
        doNow(oi);
      } catch (Exception e) {
        String si;
        try {
          si = oi.toString();
        } catch (Exception e2) {
          si = e2.toString();
        }
        if (log.isErrorEnabled()) {
          log.error("doNow["+i+"/"+n+"] failed for "+si, e);
        }
      }
    }
    tmp.clear();
  }
}
