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
import java.util.List;
import java.util.Map;

import org.cougaar.core.mts.Message;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.util.UID;

/**
 * A message between input/output pipe deliverers.
 */
public class PipeMessage extends Message {

  private final String type;
  private final UID sessionId;
  private final int counter;
  private final Map metaData;
  private final List data;

  public PipeMessage(
      MessageAddress source, MessageAddress target,
      String type,
      UID sessionId, int counter,
      Map metaData,
      List data) {
    super(source, target);
    this.type = type;
    this.sessionId = sessionId;
    this.counter = counter;
    this.metaData = metaData;
    this.data = data;

    String s = 
      (type == null ? "null type" :
       sessionId == null ? "null sessionId" : 
       counter < 0 ? ("negative counter: "+counter) :
       data == null ? "null data" : 
       null);
    if (s != null) {
      throw new IllegalArgumentException(s);
    }
  }

  /**
   * @return sender type, used to distinguish between client and server
   * messages.
   */
  public String getType() { return type; }

  /** @return unique session id */
  public UID getSessionId() { return sessionId; }

  /** @return positive sequence counter */
  public int getCounter() { return counter; }

  /** @see Deliverer#deliver */
  public Map getMetaData() { return metaData; }

  /** @see Deliverer#deliver */
  public List getData() { return data; }

  /**
   * Get a string representation of the data.
   *
   * @param prefix optional line prefix, e.g. "  ".
   * @param limit optional result string limit, or -1 for no limit
   * @return the {@link #getData} as a formatted string.
   */
  public String getDataAsString(String prefix, int limit) {
    String pre = (prefix == null ? "" : prefix);
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < data.size(); i++) {
      Object o = data.get(i);
      if (i > 0) {
        buf.append("\n");
      }
      if (o instanceof ByteArrayOutputStream) {
        o = ((ByteArrayOutputStream) o).toByteArray();
      }
      if (o instanceof byte[]) {
        byte[] b = (byte[]) o;
        buf.append(pre).append("** byte[").append(b.length).append("] **\n");
        int len = b.length;
        if (limit >= 0 && len > (limit - buf.length())) {
          len = Math.max(0, limit - buf.length());
        }
        String s = new String(b, 0, len);
        if (pre.length() > 0 && s.indexOf('\n') >= 0) {
          s = s.replaceAll("\n", "\n"+pre);
        }
        buf.append(pre).append(s);
        if (len < b.length) {
          buf.append("\n");
          if (pre.length() > 0) {
            buf.append(pre);
          }
          buf.append("..");
          break;
        }
      } else if (o == Tokens.NOOP) {
        buf.append(pre).append("** noop **");
      } else if (o == Tokens.FLUSH) {
        buf.append(pre).append("** flush **");
      } else if (o == Tokens.CLOSE) {
        buf.append(pre).append("** close **");
      } else {
        buf.append(pre).append("** ? **");
        buf.append(o == null ? "null" : o.getClass().getName());
      }
    }
    return buf.toString();
  }

  public String toString() {
    return toString(100);
  }

  public String toString(int limit) {
    return 
      "(pipe-message"+
      "\n  source="+getOriginator()+
      "\n  target="+getTarget()+
      "\n  type="+type+
      "\n  sessionId="+sessionId+
      "\n  counter="+counter+
      "\n  metaData="+
      (metaData == null ? "null" : "Map["+metaData.size()+"]")+
      "\n  data=List["+data.size()+"]"+
      "\n"+getDataAsString("    ", limit)+
      "\n  closed="+
      (!data.isEmpty() && data.get(data.size()-1) == Tokens.CLOSE)+
      ")";
  }
}
