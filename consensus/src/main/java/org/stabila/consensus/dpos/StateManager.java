package org.stabila.consensus.dpos;

import static org.stabila.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static org.stabila.core.config.Parameter.ChainConstant.MAX_ACTIVE_EXECUTIVE_NUM;

import com.google.protobuf.ByteString;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.consensus.ConsensusDelegate;
import org.stabila.consensus.base.State;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.BlockCapsule.BlockId;

@Slf4j(topic = "consensus")
@Component
public class StateManager {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Setter
  private DposService dposService;

  @Setter
  private volatile BlockId currentBlockId;

  private AtomicInteger dupBlockCount = new AtomicInteger(0);

  private AtomicLong dupBlockTime = new AtomicLong(0);

  private long blockCycle = BLOCK_PRODUCED_INTERVAL * MAX_ACTIVE_EXECUTIVE_NUM;


  public State getState() {

    if (System.currentTimeMillis() < consensusDelegate.getLatestBlockHeaderTimestamp()) {
      return State.CLOCK_ERROR;
    }

    State status = dposService.getBlockHandle().getState();
    if (!State.OK.equals(status)) {
      return status;
    }

    if (isDupExecutive()) {
      return State.DUP_EXECUTIVE;
    }

    int participation = consensusDelegate.calculateFilledSlotsCount();
    int minParticipationRate = dposService.getMinParticipationRate();
    if (participation < minParticipationRate) {
      logger
          .warn("Participation:{} <  minParticipationRate:{}", participation, minParticipationRate);
      return State.LOW_PARTICIPATION;
    }

    return State.OK;
  }

  public void receiveBlock(BlockCapsule blockCapsule) {
    if (blockCapsule.generatedByMyself) {
      currentBlockId = blockCapsule.getBlockId();
      return;
    }

    if (blockCapsule.getBlockId().equals(currentBlockId)) {
      return;
    }

    if (dposService.isNeedSyncCheck()) {
      return;
    }

    if (System.currentTimeMillis() - blockCapsule.getTimeStamp() > BLOCK_PRODUCED_INTERVAL) {
      return;
    }

    ByteString executive = blockCapsule.getExecutiveAddress();
    if (!dposService.getMiners().containsKey(executive)) {
      return;
    }

    if (dposService.getBlockHandle().getState() != State.OK) {
      dupBlockCount.set(1);
      return;
    }

    if (dupBlockCount.get() == 0) {
      dupBlockCount.set(new Random().nextInt(10));
    } else {
      dupBlockCount.set(10);
    }

    dupBlockTime.set(System.currentTimeMillis());

    logger.warn("Dup block produced: {}", blockCapsule);
  }

  private boolean isDupExecutive() {
    if (dupBlockCount.get() == 0) {
      return false;
    }
    if (System.currentTimeMillis() - dupBlockTime.get() > dupBlockCount.get() * blockCycle) {
      dupBlockCount.set(0);
      return false;
    }
    return true;
  }

}
