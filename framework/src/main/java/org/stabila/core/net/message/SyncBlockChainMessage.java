package org.stabila.core.net.message;

import java.util.List;

import org.stabila.core.capsule.BlockCapsule;
import org.stabila.protos.Protocol.BlockInventory.Type;

public class SyncBlockChainMessage extends BlockInventoryMessage {

  public SyncBlockChainMessage(byte[] packed) throws Exception {
    super(packed);
    this.type = MessageTypes.SYNC_BLOCK_CHAIN.asByte();
  }

  public SyncBlockChainMessage(List<BlockCapsule.BlockId> blockIds) {
    super(blockIds, Type.SYNC);
    this.type = MessageTypes.SYNC_BLOCK_CHAIN.asByte();
  }

  @Override
  public String toString() {
    List<BlockCapsule.BlockId> blockIdList = getBlockIds();
    StringBuilder sb = new StringBuilder();
    int size = blockIdList.size();
    sb.append(super.toString()).append("size: ").append(size);
    if (size >= 1) {
      sb.append(", start block: " + blockIdList.get(0).getString());
      if (size > 1) {
        sb.append(", end block " + blockIdList.get(blockIdList.size() - 1).getString());
      }
    }
    return sb.toString();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return ChainInventoryMessage.class;
  }
}
