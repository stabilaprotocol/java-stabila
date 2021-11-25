package org.stabila.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.protos.Protocol.DelegatedResource;

@Slf4j(topic = "capsule")
public class DelegatedResourceCapsule implements ProtoCapsule<DelegatedResource> {

  private DelegatedResource delegatedResource;

  public DelegatedResourceCapsule(final DelegatedResource delegatedResource) {
    this.delegatedResource = delegatedResource;
  }

  public DelegatedResourceCapsule(final byte[] data) {
    try {
      this.delegatedResource = DelegatedResource.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
    }
  }

  public DelegatedResourceCapsule(ByteString from, ByteString to) {
    this.delegatedResource = DelegatedResource.newBuilder()
        .setFrom(from)
        .setTo(to)
        .build();
  }

  public static byte[] createDbKey(byte[] from, byte[] to) {
    byte[] key = new byte[from.length + to.length];
    System.arraycopy(from, 0, key, 0, from.length);
    System.arraycopy(to, 0, key, from.length, to.length);
    return key;
  }

  public ByteString getFrom() {
    return this.delegatedResource.getFrom();
  }

  public ByteString getTo() {
    return this.delegatedResource.getTo();
  }

  public long getCdedBalanceForUcr() {
    return this.delegatedResource.getCdedBalanceForUcr();
  }

  public void setCdedBalanceForUcr(long ucr, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setCdedBalanceForUcr(ucr)
        .setExpireTimeForUcr(expireTime)
        .build();
  }

  public void addCdedBalanceForUcr(long ucr, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setCdedBalanceForUcr(this.delegatedResource.getCdedBalanceForUcr() + ucr)
        .setExpireTimeForUcr(expireTime)
        .build();
  }

  public long getCdedBalanceForBandwidth() {
    return this.delegatedResource.getCdedBalanceForBandwidth();
  }

  public void setCdedBalanceForBandwidth(long Bandwidth, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setCdedBalanceForBandwidth(Bandwidth)
        .setExpireTimeForBandwidth(expireTime)
        .build();
  }

  public void addCdedBalanceForBandwidth(long Bandwidth, long expireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setCdedBalanceForBandwidth(this.delegatedResource.getCdedBalanceForBandwidth()
            + Bandwidth)
        .setExpireTimeForBandwidth(expireTime)
        .build();
  }

  public long getExpireTimeForBandwidth() {
    return this.delegatedResource.getExpireTimeForBandwidth();
  }

  public long getExpireTimeForUcr() {
    return this.delegatedResource.getExpireTimeForUcr();
  }

  public void setExpireTimeForBandwidth(long ExpireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setExpireTimeForBandwidth(ExpireTime)
        .build();
  }

  public long getExpireTimeForUcr(DynamicPropertiesStore dynamicPropertiesStore) {
    if (dynamicPropertiesStore.getAllowMultiSign() == 0) {
      return this.delegatedResource.getExpireTimeForBandwidth();
    } else {
      return this.delegatedResource.getExpireTimeForUcr();
    }
  }

  public void setExpireTimeForUcr(long ExpireTime) {
    this.delegatedResource = this.delegatedResource.toBuilder()
        .setExpireTimeForUcr(ExpireTime)
        .build();
  }

  public byte[] createDbKey() {
    return createDbKey(this.delegatedResource.getFrom().toByteArray(),
        this.delegatedResource.getTo().toByteArray());
  }

  @Override
  public byte[] getData() {
    return this.delegatedResource.toByteArray();
  }

  @Override
  public DelegatedResource getInstance() {
    return this.delegatedResource;
  }

}
