package org.stabila.core.capsule;

import static org.stabila.common.crypto.Hash.computeAddress;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.stabila.common.utils.ByteArray;
import org.stabila.protos.Protocol.Executive;

@Slf4j(topic = "capsule")
public class ExecutiveCapsule implements ProtoCapsule<Executive>, Comparable<ExecutiveCapsule> {

  private Executive executive;


  /**
   * ExecutiveCapsule constructor with pubKey and url.
   */
  public ExecutiveCapsule(final ByteString pubKey, final String url) {
    final Executive.Builder executiveBuilder = Executive.newBuilder();
    this.executive = executiveBuilder
        .setPubKey(pubKey)
        .setAddress(ByteString.copyFrom(computeAddress(pubKey.toByteArray())))
        .setUrl(url).build();
  }

  public ExecutiveCapsule(final Executive executive) {
    this.executive = executive;
  }

  /**
   * ExecutiveCapsule constructor with address.
   */
  public ExecutiveCapsule(final ByteString address) {
    this.executive = Executive.newBuilder().setAddress(address).build();
  }

  /**
   * ExecutiveCapsule constructor with address and voteCount.
   */
  public ExecutiveCapsule(final ByteString address, final long voteCount, final String url) {
    final Executive.Builder executiveBuilder = Executive.newBuilder();
    this.executive = executiveBuilder
        .setAddress(address)
        .setVoteCount(voteCount).setUrl(url).build();
  }

  public ExecutiveCapsule(final byte[] data) {
    try {
      this.executive = Executive.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  @Override
  public int compareTo(ExecutiveCapsule otherObject) {
    return Long.compare(otherObject.getVoteCount(), this.getVoteCount());
  }

  public ByteString getAddress() {
    return this.executive.getAddress();
  }

  public byte[] createDbKey() {
    return getAddress().toByteArray();
  }

  public String createReadableString() {
    return ByteArray.toHexString(getAddress().toByteArray());
  }

  @Override
  public byte[] getData() {
    return this.executive.toByteArray();
  }

  @Override
  public Executive getInstance() {
    return this.executive;
  }

  public void setPubKey(final ByteString pubKey) {
    this.executive = this.executive.toBuilder().setPubKey(pubKey).build();
  }

  public long getVoteCount() {
    return this.executive.getVoteCount();
  }

  public void setVoteCount(final long voteCount) {
    this.executive = this.executive.toBuilder().setVoteCount(voteCount).build();
  }

  public long getTotalProduced() {
    return this.executive.getTotalProduced();
  }

  public void setTotalProduced(final long totalProduced) {
    this.executive = this.executive.toBuilder().setTotalProduced(totalProduced).build();
  }

  public long getTotalMissed() {
    return this.executive.getTotalMissed();
  }

  public void setTotalMissed(final long totalMissed) {
    this.executive = this.executive.toBuilder().setTotalMissed(totalMissed).build();
  }

  public long getLatestBlockNum() {
    return this.executive.getLatestBlockNum();
  }

  public void setLatestBlockNum(final long latestBlockNum) {
    this.executive = this.executive.toBuilder().setLatestBlockNum(latestBlockNum).build();
  }

  public long getLatestSlotNum() {
    return this.executive.getLatestSlotNum();
  }

  public void setLatestSlotNum(final long latestSlotNum) {
    this.executive = this.executive.toBuilder().setLatestSlotNum(latestSlotNum).build();
  }

  public boolean getIsJobs() {
    return this.executive.getIsJobs();
  }

  public void setIsJobs(final boolean isJobs) {
    this.executive = this.executive.toBuilder().setIsJobs(isJobs).build();
  }

  public String getUrl() {
    return this.executive.getUrl();
  }

  public void setUrl(final String url) {
    this.executive = this.executive.toBuilder().setUrl(url).build();
  }
}
