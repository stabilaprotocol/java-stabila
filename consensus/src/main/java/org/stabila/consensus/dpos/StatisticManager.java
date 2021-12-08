package org.stabila.consensus.dpos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.consensus.ConsensusDelegate;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;

@Slf4j(topic = "consensus")
@Component
public class StatisticManager {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private DposSlot dposSlot;

  public void applyBlock(BlockCapsule blockCapsule) {
    ExecutiveCapsule wc;
    long blockNum = blockCapsule.getNum();
    long blockTime = blockCapsule.getTimeStamp();
    byte[] blockExecutive = blockCapsule.getExecutiveAddress().toByteArray();
    wc = consensusDelegate.getExecutive(blockExecutive);
    wc.setTotalProduced(wc.getTotalProduced() + 1);
    wc.setLatestBlockNum(blockNum);
    wc.setLatestSlotNum(dposSlot.getAbSlot(blockTime));
    consensusDelegate.saveExecutive(wc);

    long slot = 1;
    if (blockNum != 1) {
      slot = dposSlot.getSlot(blockTime);
    }
    for (int i = 1; i < slot; ++i) {
      byte[] executive = dposSlot.getScheduledExecutive(i).toByteArray();
      wc = consensusDelegate.getExecutive(executive);
      wc.setTotalMissed(wc.getTotalMissed() + 1);
      consensusDelegate.saveExecutive(wc);
      logger.info("Current block: {}, executive: {} totalMissed: {}",
          blockNum, wc.createReadableString(), wc.getTotalMissed());
      consensusDelegate.applyBlock(false);
    }
    consensusDelegate.applyBlock(true);
  }
}