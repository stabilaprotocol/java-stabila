package org.stabila.consensus.dpos;

import static org.stabila.core.config.Parameter.ChainConstant.MAX_ACTIVE_EXECUTIVE_NUM;
import static org.stabila.core.config.Parameter.ChainConstant.SOLIDIFIED_THRESHOLD;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.common.args.GenesisBlock;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.ByteArray;
import org.stabila.consensus.ConsensusDelegate;
import org.stabila.consensus.base.BlockHandle;
import org.stabila.consensus.base.ConsensusInterface;
import org.stabila.consensus.base.Param;
import org.stabila.consensus.base.Param.Miner;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;

@Slf4j(topic = "consensus")
@Component
public class DposService implements ConsensusInterface {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private DposTask dposTask;

  @Autowired
  private DposSlot dposSlot;

  @Autowired
  private StateManager stateManager;

  @Autowired
  private StatisticManager statisticManager;

  @Autowired
  private MaintenanceManager maintenanceManager;

  @Getter
  @Setter
  private volatile boolean needSyncCheck;
  @Getter
  private volatile boolean enable;
  @Getter
  private int minParticipationRate;
  @Getter
  private int blockProduceTimeoutPercent;
  @Getter
  private long genesisBlockTime;
  @Getter
  private BlockHandle blockHandle;
  @Getter
  private GenesisBlock genesisBlock;
  @Getter
  private Map<ByteString, Miner> miners = new HashMap<>();

  @Override
  public void start(Param param) {
    this.enable = param.isEnable();
    this.needSyncCheck = param.isNeedSyncCheck();
    this.minParticipationRate = param.getMinParticipationRate();
    this.blockProduceTimeoutPercent = param.getBlockProduceTimeoutPercent();
    this.blockHandle = param.getBlockHandle();
    this.genesisBlock = param.getGenesisBlock();
    this.genesisBlockTime = Long.parseLong(param.getGenesisBlock().getTimestamp());
    param.getMiners().forEach(miner -> miners.put(miner.getExecutiveAddress(), miner));

    dposTask.setDposService(this);
    dposSlot.setDposService(this);
    stateManager.setDposService(this);
    maintenanceManager.setDposService(this);

    if (consensusDelegate.getLatestBlockHeaderNumber() == 0) {
      List<ByteString> executives = new ArrayList<>();
      consensusDelegate.getAllExecutives().forEach(executiveCapsule ->
          executives.add(executiveCapsule.getAddress()));
      updateExecutive(executives);
      List<ByteString> addresses = consensusDelegate.getActiveExecutives();
      addresses.forEach(address -> {
        ExecutiveCapsule executiveCapsule = consensusDelegate.getExecutive(address.toByteArray());
        executiveCapsule.setIsJobs(true);
        consensusDelegate.saveExecutive(executiveCapsule);
      });
    }
    maintenanceManager.init();
    dposTask.init();
  }

  @Override
  public void stop() {
    dposTask.stop();
  }

  @Override
  public void receiveBlock(BlockCapsule blockCapsule) {
    stateManager.receiveBlock(blockCapsule);
  }

  @Override
  public boolean validBlock(BlockCapsule blockCapsule) {
    if (consensusDelegate.getLatestBlockHeaderNumber() == 0) {
      return true;
    }
    ByteString executiveAddress = blockCapsule.getExecutiveAddress();
    long timeStamp = blockCapsule.getTimeStamp();
    long bSlot = dposSlot.getAbSlot(timeStamp);
    long hSlot = dposSlot.getAbSlot(consensusDelegate.getLatestBlockHeaderTimestamp());
    if (bSlot <= hSlot) {
      logger.warn("ValidBlock failed: bSlot: {} <= hSlot: {}", bSlot, hSlot);
      return false;
    }

    long slot = dposSlot.getSlot(timeStamp);
    final ByteString scheduledExecutive = dposSlot.getScheduledExecutive(slot);
    if (!scheduledExecutive.equals(executiveAddress)) {
      logger.warn("ValidBlock failed: sExecutive: {}, bExecutive: {}, bTimeStamp: {}, slot: {}",
          ByteArray.toHexString(scheduledExecutive.toByteArray()),
          ByteArray.toHexString(executiveAddress.toByteArray()), new DateTime(timeStamp), slot);
      return false;
    }

    return true;
  }

  @Override
  public boolean applyBlock(BlockCapsule blockCapsule) {
    statisticManager.applyBlock(blockCapsule);
    maintenanceManager.applyBlock(blockCapsule);
    updateSolidBlock();
    return true;
  }

  private void updateSolidBlock() {
    List<Long> numbers = consensusDelegate.getActiveExecutives().stream()
        .map(address -> consensusDelegate.getExecutive(address.toByteArray()).getLatestBlockNum())
        .sorted()
        .collect(Collectors.toList());
    long size = consensusDelegate.getActiveExecutives().size();
    int position = (int) (size * (1 - SOLIDIFIED_THRESHOLD * 1.0 / 100));
    long newSolidNum = numbers.get(position);
    long oldSolidNum = consensusDelegate.getLatestSolidifiedBlockNum();
    if (newSolidNum < oldSolidNum) {
      logger.warn("Update solid block number failed, new: {} < old: {}", newSolidNum, oldSolidNum);
      return;
    }
    CommonParameter.getInstance()
        .setOldSolidityBlockNum(consensusDelegate.getLatestSolidifiedBlockNum());
    consensusDelegate.saveLatestSolidifiedBlockNum(newSolidNum);
    logger.info("Update solid block number to {}", newSolidNum);
  }

  public void updateExecutive(List<ByteString> list) {
    list.sort(Comparator.comparingLong((ByteString b) ->
        consensusDelegate.getExecutive(b.toByteArray()).getVoteCount())
        .reversed()
        .thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));

    if (list.size() > MAX_ACTIVE_EXECUTIVE_NUM) {
      consensusDelegate
          .saveActiveExecutives(list.subList(0, MAX_ACTIVE_EXECUTIVE_NUM));
    } else {
      consensusDelegate.saveActiveExecutives(list);
    }
  }

}
