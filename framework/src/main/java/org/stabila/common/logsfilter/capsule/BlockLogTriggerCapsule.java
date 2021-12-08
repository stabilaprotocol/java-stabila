package org.stabila.common.logsfilter.capsule;

import lombok.Getter;
import lombok.Setter;
import org.stabila.common.logsfilter.EventPluginLoader;
import org.stabila.common.logsfilter.trigger.BlockLogTrigger;
import org.stabila.core.capsule.BlockCapsule;

public class BlockLogTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private BlockLogTrigger blockLogTrigger;

  public BlockLogTriggerCapsule(BlockCapsule block) {
    blockLogTrigger = new BlockLogTrigger();
    blockLogTrigger.setBlockHash(block.getBlockId().toString());
    blockLogTrigger.setTimeStamp(block.getTimeStamp());
    blockLogTrigger.setBlockNumber(block.getNum());
    blockLogTrigger.setTransactionSize(block.getTransactions().size());
    block.getTransactions().forEach(stb ->
        blockLogTrigger.getTransactionList().add(stb.getTransactionId().toString())
    );
  }

  public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
    blockLogTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postBlockTrigger(blockLogTrigger);
  }
}
