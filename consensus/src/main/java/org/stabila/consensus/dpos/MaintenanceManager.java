package org.stabila.consensus.dpos;

import static org.stabila.common.utils.WalletUtil.getAddressStringList;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.consensus.ConsensusDelegate;
import org.stabila.consensus.pbft.PbftManager;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.VotesCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.store.DelegationStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.VotesStore;

@Slf4j(topic = "consensus")
@Component
public class MaintenanceManager {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private IncentiveManager incentiveManager;

  @Setter
  private DposService dposService;

  @Setter
  private PbftManager pbftManager;

  @Getter
  private final List<ByteString> beforeExecutive = new ArrayList<>();
  @Getter
  private final List<ByteString> currentExecutive = new ArrayList<>();
  @Getter
  private long beforeMaintenanceTime;

  public void init() {
    currentExecutive.addAll(consensusDelegate.getActiveExecutives());
  }

  public void applyBlock(BlockCapsule blockCapsule) {
    long blockNum = blockCapsule.getNum();
    long blockTime = blockCapsule.getTimeStamp();
    long nextMaintenanceTime = consensusDelegate.getNextMaintenanceTime();
    boolean flag = consensusDelegate.getNextMaintenanceTime() <= blockTime;
    if (flag) {
      if (blockNum != 1) {
        updateExecutiveValue(beforeExecutive);
        beforeMaintenanceTime = nextMaintenanceTime;
        doMaintenance();
        updateExecutiveValue(currentExecutive);
      }
      consensusDelegate.updateNextMaintenanceTime(blockTime);
      if (blockNum != 1) {
        //pbft sr msg
        pbftManager.srPrePrepare(blockCapsule, currentExecutive,
            consensusDelegate.getNextMaintenanceTime());
      }
    }
    consensusDelegate.saveStateFlag(flag ? 1 : 0);
    //pbft block msg
    if (blockNum == 1) {
      nextMaintenanceTime = consensusDelegate.getNextMaintenanceTime();
    }
    pbftManager.blockPrePrepare(blockCapsule, nextMaintenanceTime);
  }

  private void updateExecutiveValue(List<ByteString> srList) {
    srList.clear();
    srList.addAll(consensusDelegate.getActiveExecutives());
  }

  public void doMaintenance() {
    VotesStore votesStore = consensusDelegate.getVotesStore();

    tryRemoveThePowerOfTheGr();

    DynamicPropertiesStore dynamicPropertiesStore = consensusDelegate.getDynamicPropertiesStore();
    DelegationStore delegationStore = consensusDelegate.getDelegationStore();
    if (dynamicPropertiesStore.useNewRewardAlgorithm()) {
      long curCycle = dynamicPropertiesStore.getCurrentCycleNumber();
      consensusDelegate.getAllExecutives().forEach(executive -> {
        delegationStore.accumulateExecutiveVi(curCycle, executive.createDbKey(), executive.getVoteCount());
      });
    }

    Map<ByteString, Long> countExecutive = countVote(votesStore);
    if (!countExecutive.isEmpty()) {
      List<ByteString> currentWits = consensusDelegate.getActiveExecutives();

      List<ByteString> newExecutiveAddressList = new ArrayList<>();
      consensusDelegate.getAllExecutives()
          .forEach(executiveCapsule -> newExecutiveAddressList.add(executiveCapsule.getAddress()));

      countExecutive.forEach((address, voteCount) -> {
        byte[] executiveAddress = address.toByteArray();
        ExecutiveCapsule executiveCapsule = consensusDelegate.getExecutive(executiveAddress);
        if (executiveCapsule == null) {
          logger.warn("Executive capsule is null. address is {}", Hex.toHexString(executiveAddress));
          return;
        }
        AccountCapsule account = consensusDelegate.getAccount(executiveAddress);
        if (account == null) {
          logger.warn("Executive account is null. address is {}", Hex.toHexString(executiveAddress));
          return;
        }
        executiveCapsule.setVoteCount(executiveCapsule.getVoteCount() + voteCount);
        consensusDelegate.saveExecutive(executiveCapsule);
        logger.info("address is {} , countVote is {}", executiveCapsule.createReadableString(),
            executiveCapsule.getVoteCount());
      });

      dposService.updateExecutive(newExecutiveAddressList);

      incentiveManager.reward(newExecutiveAddressList);

      List<ByteString> newWits = consensusDelegate.getActiveExecutives();
      if (!CollectionUtils.isEqualCollection(currentWits, newWits)) {
        currentWits.forEach(address -> {
          ExecutiveCapsule executiveCapsule = consensusDelegate.getExecutive(address.toByteArray());
          executiveCapsule.setIsJobs(false);
          consensusDelegate.saveExecutive(executiveCapsule);
        });
        newWits.forEach(address -> {
          ExecutiveCapsule executiveCapsule = consensusDelegate.getExecutive(address.toByteArray());
          executiveCapsule.setIsJobs(true);
          consensusDelegate.saveExecutive(executiveCapsule);
        });
      }

      logger.info("Update executive success. \nbefore: {} \nafter: {}",
          getAddressStringList(currentWits),
          getAddressStringList(newWits));
    }

    if (dynamicPropertiesStore.allowChangeDelegation()) {
      long nextCycle = dynamicPropertiesStore.getCurrentCycleNumber() + 1;
      dynamicPropertiesStore.saveCurrentCycleNumber(nextCycle);
      consensusDelegate.getAllExecutives().forEach(executive -> {
        delegationStore.setBrokerage(nextCycle, executive.createDbKey(),
            delegationStore.getBrokerage(executive.createDbKey()));
        delegationStore.setExecutiveVote(nextCycle, executive.createDbKey(), executive.getVoteCount());
      });
    }
  }

  private Map<ByteString, Long> countVote(VotesStore votesStore) {
    final Map<ByteString, Long> countExecutive = Maps.newHashMap();
    Iterator<Entry<byte[], VotesCapsule>> dbIterator = votesStore.iterator();
    long sizeCount = 0;
    while (dbIterator.hasNext()) {
      Entry<byte[], VotesCapsule> next = dbIterator.next();
      VotesCapsule votes = next.getValue();
      votes.getOldVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        if (countExecutive.containsKey(voteAddress)) {
          countExecutive.put(voteAddress, countExecutive.get(voteAddress) - voteCount);
        } else {
          countExecutive.put(voteAddress, -voteCount);
        }
      });
      votes.getNewVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        if (countExecutive.containsKey(voteAddress)) {
          countExecutive.put(voteAddress, countExecutive.get(voteAddress) + voteCount);
        } else {
          countExecutive.put(voteAddress, voteCount);
        }
      });
      sizeCount++;
      votesStore.delete(next.getKey());
    }
    logger.info("There is {} new votes in this epoch", sizeCount);
    return countExecutive;
  }

  private void tryRemoveThePowerOfTheGr() {
    if (consensusDelegate.getRemoveThePowerOfTheGr() != 1) {
      return;
    }
    dposService.getGenesisBlock().getExecutives().forEach(executive -> {
      ExecutiveCapsule executiveCapsule = consensusDelegate.getExecutive(executive.getAddress());
      executiveCapsule.setVoteCount(executiveCapsule.getVoteCount() - executive.getVoteCount());
      consensusDelegate.saveExecutive(executiveCapsule);
    });
    consensusDelegate.saveRemoveThePowerOfTheGr(-1);
  }

}
