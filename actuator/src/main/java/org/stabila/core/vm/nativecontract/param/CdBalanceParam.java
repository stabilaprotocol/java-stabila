package org.stabila.core.vm.nativecontract.param;

import org.stabila.protos.contract.Common;

public class CdBalanceParam {

  private byte[] ownerAddress;

  private byte[] receiverAddress;

  private long cdedBalance;

  private long cdedDuration;

  private Common.ResourceCode resourceType;

  private boolean isDelegating;

  public byte[] getOwnerAddress() {
    return ownerAddress;
  }

  public void setOwnerAddress(byte[] ownerAddress) {
    this.ownerAddress = ownerAddress;
  }

  public byte[] getReceiverAddress() {
    return receiverAddress;
  }

  public void setReceiverAddress(byte[] receiverAddress) {
    this.receiverAddress = receiverAddress;
  }

  public long getCdedBalance() {
    return cdedBalance;
  }

  public void setCdedBalance(long cdedBalance) {
    this.cdedBalance = cdedBalance;
  }

  public long getCdedDuration() {
    return cdedDuration;
  }

  public void setCdedDuration(long cdedDuration) {
    this.cdedDuration = cdedDuration;
  }

  public Common.ResourceCode getResourceType() {
    return resourceType;
  }

  public void setResourceType(Common.ResourceCode resourceType) {
    this.resourceType = resourceType;
  }

  public boolean isDelegating() {
    return isDelegating;
  }

  public void setDelegating(boolean delegating) {
    isDelegating = delegating;
  }
}
