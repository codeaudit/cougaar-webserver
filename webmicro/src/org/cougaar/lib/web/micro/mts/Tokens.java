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

import java.io.Serializable;

/**
 * These tokens are marker objects in a {@link Deliverer} data list, and
 * are used to separate <code>byte[]</code> stream data from control options
 * (flush/close/keep-alive).
 */
public final class Tokens {

  /** Noop to keep stream alive, can occur anywhere in the list */
  public static final NoOp NOOP = NoOp.INSTANCE;

  /** Flush the stream, can occur anywhere in the list */
  public static final Flush FLUSH = Flush.INSTANCE;

  /** Close the stream, should only occur at the end of the list */
  public static final Close CLOSE = Close.INSTANCE;

  private static final class NoOp implements Serializable {
    public static final NoOp INSTANCE = new NoOp();
    private NoOp() { }
    private Object readResolve() { return INSTANCE; }
    public String toString() { return "noop"; }
    static final long serialVersionUID = 1128390689504398535L;
  }

  private static final class Flush implements Serializable {
    public static final Flush INSTANCE = new Flush();
    private Flush() { }
    private Object readResolve() { return INSTANCE; }
    public String toString() { return "flush"; }
    static final long serialVersionUID = 1238901293812098314L;
  }

  private static final class Close implements Serializable {
    public static final Close INSTANCE = new Close();
    private Close() { }
    private Object readResolve() { return INSTANCE; }
    public String toString() { return "close"; }
    static final long serialVersionUID = 3273897829389628323L;
  }
}
