package org.stabila.core.net.message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.stabila.core.capsule.BlockCapsule;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.BlockInventory;

public class BlockInventoryMessage extends TronMessage {

  protected BlockInventory blockInventory;

  public BlockInventoryMessage(byte[] data) throws Exception {
    super(data);
    this.type = MessageTypes.BLOCK_INVENTORY.asByte();
    this.blockInventory = Protocol.BlockInventory.parseFrom(data);
  }

  public BlockInventoryMessage(List<BlockCapsule.BlockId> blockIds, BlockInventory.Type type) {
    BlockInventory.Builder invBuilder = BlockInventory.newBuilder();
    blockIds.forEach(blockId -> {
      BlockInventory.BlockId.Builder b = BlockInventory.BlockId.newBuilder();
      b.setHash(blockId.getByteString());
      b.setNumber(blockId.getNum());
      invBuilder.addIds(b);
    });

    invBuilder.setType(type);
    blockInventory = invBuilder.build();
    this.type = MessageTypes.BLOCK_INVENTORY.asByte();
    this.data = blockInventory.toByteArray();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  private BlockInventory getBlockInventory() {
    return blockInventory;
  }

  public List<BlockCapsule.BlockId> getBlockIds() {
    return getBlockInventory().getIdsList().stream()
        .map(blockId -> new BlockCapsule.BlockId(blockId.getHash(), blockId.getNumber()))
        .collect(Collectors.toCollection(ArrayList::new));
  }

}
