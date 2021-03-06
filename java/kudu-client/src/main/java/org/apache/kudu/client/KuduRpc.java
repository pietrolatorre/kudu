/*
 * Copyright (C) 2010-2012  The Async HBase Authors.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the StumbleUpon nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.apache.kudu.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.stumbleupon.async.Deferred;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.apache.kudu.annotations.InterfaceAudience;
import org.apache.kudu.util.Pair;
import org.apache.kudu.util.Slice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

import static org.apache.kudu.client.ExternalConsistencyMode.CLIENT_PROPAGATED;

/**
 * Abstract base class for all RPC requests going out to Kudu.
 * <p>
 * Implementations of this class are <b>not</b> expected to be synchronized.
 *
 * <h1>A note on passing {@code byte} arrays in argument</h1>
 * None of the method that receive a {@code byte[]} in argument will copy it.
 * If you change the contents of any byte array you give to an instance of
 * this class, you <em>may</em> affect the behavior of the request in an
 * <strong>unpredictable</strong> way.  If you need to change the byte array,
 * {@link Object#clone() clone} it before giving it to this class.  For those
 * familiar with the term "defensive copy", we don't do it in order to avoid
 * unnecessary memory copies when you know you won't be changing (or event
 * holding a reference to) the byte array, which is frequently the case.
 */
@InterfaceAudience.Private
public abstract class KuduRpc<R> {

  // Service names.
  protected static final String MASTER_SERVICE_NAME = "kudu.master.MasterService";
  protected static final String TABLET_SERVER_SERVICE_NAME = "kudu.tserver.TabletServerService";

  private static final Logger LOG = LoggerFactory.getLogger(KuduRpc.class);

  /**
   * Returns the partition key this RPC is for, or {@code null} if the RPC is
   * not tablet specific.
   * <p>
   * <strong>DO NOT MODIFY THE CONTENTS OF THE RETURNED ARRAY.</strong>
   */
  byte[] partitionKey() {
    return null;
  }

  /**
   * The Deferred that will be invoked when this RPC completes or fails.
   * In case of a successful completion, this Deferred's first callback
   * will be invoked with an {@link Object} containing the de-serialized
   * RPC response in argument.
   * Once an RPC has been used, we create a new Deferred for it, in case
   * the user wants to re-use it.
   */
  private Deferred<R> deferred;

  private AsyncKuduClient.RemoteTablet tablet;

  final KuduTable table;

  final DeadlineTracker deadlineTracker;

  protected long propagatedTimestamp = -1;
  protected ExternalConsistencyMode externalConsistencyMode = CLIENT_PROPAGATED;

  /**
   * How many times have we retried this RPC?.
   * Proper synchronization is required, although in practice most of the code
   * that access this attribute will have a happens-before relationship with
   * the rest of the code, due to other existing synchronization.
   */
  byte attempt;  // package-private for TabletClient and AsyncKuduClient only.

  /**
   * Set by TabletClient when isRequestTracked returns true to identify this RPC in the sequence of
   * RPCs sent by this client. Once it is set it should never change unless the RPC is reused.
   */
  long sequenceId = RequestTracker.NO_SEQ_NO;

  KuduRpc(KuduTable table) {
    this.table = table;
    this.deadlineTracker = new DeadlineTracker();
  }

  /**
   * To be implemented by the concrete sub-type.
   *
   * Notice that this method is package-private, so only classes within this
   * package can use this as a base class.
   */
  abstract ChannelBuffer serialize(Message header);

  /**
   * Package private way of getting the name of the RPC service.
   */
  abstract String serviceName();

  /**
   * Package private way of getting the name of the RPC method.
   */
  abstract String method();

  /**
   * Returns the set of application-specific feature flags required to service the RPC.
   * @return the feature flags required to complete the RPC
   */
  Collection<Integer> getRequiredFeatures() {
    return ImmutableList.of();
  }

  /**
   * To be implemented by the concrete sub-type.
   * This method is expected to de-serialize a response received for the
   * current RPC.
   *
   * Notice that this method is package-private, so only classes within this
   * package can use this as a base class.
   *
   * @param callResponse The call response from which to deserialize.
   * @param tsUUID A string that contains the UUID of the server that answered the RPC.
   * @return An Object of type R that will be sent to callback and an Object that will be an Error
   * of type TabletServerErrorPB or MasterErrorPB that will be converted into an exception and
   * sent to errback.
   * @throws Exception An exception that will be sent to errback.
   */
  abstract Pair<R, Object> deserialize(CallResponse callResponse, String tsUUID) throws Exception;

  /**
   * Update the statistics information before this rpc is called back. This method should not throw
   * any exception, including RuntimeException. This method does nothing by default.
   *
   * @param statistics object to update
   * @param response of this rpc
   */
  void updateStatistics(Statistics statistics, R response){
    // default do nothing
  }

  /**
   * Sets the external consistency mode for this RPC.
   * TODO make this cover most if not all RPCs (right now only scans and writes use this).
   * @param externalConsistencyMode the mode to set
   */
  public void setExternalConsistencyMode(ExternalConsistencyMode externalConsistencyMode) {
    this.externalConsistencyMode = externalConsistencyMode;
  }

  public ExternalConsistencyMode getExternalConsistencyMode() {
    return this.externalConsistencyMode;
  }

  /**
   * Sets the propagated timestamp for this RPC.
   * @param propagatedTimestamp the timestamp to propagate
   */
  public void setPropagatedTimestamp(long propagatedTimestamp) {
    this.propagatedTimestamp = propagatedTimestamp;
  }

  private void handleCallback(final Object result) {
    final Deferred<R> d = deferred;
    if (d == null) {
      return;
    }
    deferred = null;
    attempt = 0;
    if (isRequestTracked()) {
      table.getAsyncClient().getRequestTracker().rpcCompleted(sequenceId);
      sequenceId = RequestTracker.NO_SEQ_NO;
    }
    deadlineTracker.reset();
    d.callback(result);
  }

  /**
   * Package private way of making an RPC complete by giving it its result.
   * If this RPC has no {@link Deferred} associated to it, nothing will
   * happen.  This may happen if the RPC was already called back.
   * <p>
   * Once this call to this method completes, this object can be re-used to
   * re-send the same RPC, provided that no other thread still believes this
   * RPC to be in-flight (guaranteeing this may be hard in error cases).
   */
  final void callback(final R result) {
    handleCallback(result);
  }

  /**
   * Same as callback, except that it accepts an Exception.
   */
  final void errback(final Exception e) {
    handleCallback(e);
  }

  /** Package private way of accessing / creating the Deferred of this RPC.  */
  final Deferred<R> getDeferred() {
    if (deferred == null) {
      deferred = new Deferred<R>();
    }
    return deferred;
  }

  AsyncKuduClient.RemoteTablet getTablet() {
    return this.tablet;
  }

  void setTablet(AsyncKuduClient.RemoteTablet tablet) {
    this.tablet = tablet;
  }

  public KuduTable getTable() {
    return table;
  }

  void setTimeoutMillis(long timeout) {
    deadlineTracker.setDeadline(timeout);
  }

  /**
   * If this RPC needs to be tracked on the client and server-side. Some RPCs require exactly-once
   * semantics which is enabled by tracking them.
   * @return true if the request has to be tracked, else false
   */
  boolean isRequestTracked() {
    return false;
  }

  long getSequenceId() {
    return sequenceId;
  }

  void setSequenceId(long sequenceId) {
    assert (this.sequenceId == RequestTracker.NO_SEQ_NO);
    this.sequenceId = sequenceId;
  }

  public String toString() {

    final StringBuilder buf = new StringBuilder();
    buf.append("KuduRpc(method=");
    buf.append(method());
    buf.append(", tablet=");
    if (tablet == null) {
      buf.append("null");
    } else {
      buf.append(tablet.getTabletIdAsString());
    }
    buf.append(", attempt=").append(attempt);
    buf.append(", ").append(deadlineTracker);
    // Cheating a bit, we're not actually logging but we'll augment the information provided by
    // this method if DEBUG is enabled.
    if (LOG.isDebugEnabled()) {
      buf.append(", ").append(deferred);
    }
    buf.append(')');
    return buf.toString();
  }

  static void readProtobuf(final Slice slice,
      final com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    final int length = slice.length();
    final byte[] payload = slice.getRawArray();
    final int offset = slice.getRawOffset();
    try {
      builder.mergeFrom(payload, offset, length);
      if (!builder.isInitialized()) {
        throw new RuntimeException("Could not deserialize the response," +
            " incompatible RPC? Error is: " + builder.getInitializationErrorString());
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Invalid RPC response: length=" + length +
            ", payload=" + Bytes.pretty(payload));
    }
  }

  static ChannelBuffer toChannelBuffer(Message header, Message pb) {
    int totalSize = IPCUtil.getTotalSizeWhenWrittenDelimited(header, pb);
    byte[] buf = new byte[totalSize+4];
    ChannelBuffer chanBuf = ChannelBuffers.wrappedBuffer(buf);
    chanBuf.clear();
    chanBuf.writeInt(totalSize);
    final CodedOutputStream out = CodedOutputStream.newInstance(buf, 4, totalSize);
    try {
      out.writeRawVarint32(header.getSerializedSize());
      header.writeTo(out);

      out.writeRawVarint32(pb.getSerializedSize());
      pb.writeTo(out);
      out.checkNoSpaceLeft();
    } catch (IOException e) {
      throw new RuntimeException("Cannot serialize the following message " + pb);
    }
    chanBuf.writerIndex(buf.length);
    return chanBuf;
  }

  /**
   * Upper bound on the size of a byte array we de-serialize.
   * This is to prevent Kudu from OOM'ing us, should there be a bug or
   * undetected corruption of an RPC on the network, which would turn a
   * an innocuous RPC into something allocating a ton of memory.
   * The Hadoop RPC protocol doesn't do any checksumming as they probably
   * assumed that TCP checksums would be sufficient (they're not).
   */
  static final long MAX_BYTE_ARRAY_MASK =
      0xFFFFFFFFF0000000L;  // => max = 256MB

  /**
   * Verifies that the given length looks like a reasonable array length.
   * This method accepts 0 as a valid length.
   * @param buf The buffer from which the length was read.
   * @param length The length to validate.
   * @throws IllegalArgumentException if the length is negative or
   * suspiciously large.
   */
  static void checkArrayLength(final ChannelBuffer buf, final long length) {
    // 2 checks in 1.  If any of the high bits are set, we know the value is
    // either too large, or is negative (if the most-significant bit is set).
    if ((length & MAX_BYTE_ARRAY_MASK) != 0) {
      if (length < 0) {
        throw new IllegalArgumentException("Read negative byte array length: "
            + length + " in buf=" + buf + '=' + Bytes.pretty(buf));
      } else {
        throw new IllegalArgumentException("Read byte array length that's too"
            + " large: " + length + " > " + ~MAX_BYTE_ARRAY_MASK + " in buf="
            + buf + '=' + Bytes.pretty(buf));
      }
    }
  }
}
