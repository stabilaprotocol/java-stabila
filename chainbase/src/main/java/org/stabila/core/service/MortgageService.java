package org.stabila.core.service;

import com.google.protobuf.ByteString;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DelegationStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.ExecutiveStore;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.config.Parameter.ChainConstant;
import org.stabila.core.exception.BalanceInsufficientException;
import org.stabila.protos.Protocol.Vote;

@Slf4j(topic = "mortgage")
@Component
public class MortgageService {

  @Setter
  private ExecutiveStore executiveStore;

  @Setter
  @Getter
  private DelegationStore delegationStore;

  @Setter
  private DynamicPropertiesStore dynamicPropertiesStore;

  @Setter
  private AccountStore accountStore;

  public void initStore(ExecutiveStore executiveStore, DelegationStore delegationStore,
                        DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore) {
    this.executiveStore = executiveStore;
    this.delegationStore = delegationStore;
    this.dynamicPropertiesStore = dynamicPropertiesStore;
    this.accountStore = accountStore;
  }

  public void payStandbyExecutive() {
    List<ExecutiveCapsule> executiveCapsules = executiveStore.getAllExecutives();
    Map<ByteString, ExecutiveCapsule> executiveCapsuleMap = new HashMap<>();
    List<ByteString> executiveAddressList = new ArrayList<>();
    for (ExecutiveCapsule executiveCapsule : executiveCapsules) {
      executiveAddressList.add(executiveCapsule.getAddress());
      executiveCapsuleMap.put(executiveCapsule.getAddress(), executiveCapsule);
    }
    executiveAddressList.sort(Comparator.comparingLong((ByteString b) -> executiveCapsuleMap.get(b).getVoteCount())
            .reversed().thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));
    if (executiveAddressList.size() > ChainConstant.EXECUTIVE_STANDBY_LENGTH) {
      executiveAddressList = executiveAddressList.subList(0, ChainConstant.EXECUTIVE_STANDBY_LENGTH);
    }
    long voteSum = 0;
    long totalPay = dynamicPropertiesStore.getExecutive100PayPerBlock();
    for (ByteString b : executiveAddressList) {
      voteSum += executiveCapsuleMap.get(b).getVoteCount();
    }

    if (voteSum > 0) {
      for (ByteString b : executiveAddressList) {
        double eachVotePay = (double) totalPay / voteSum;
        long pay = (long) (executiveCapsuleMap.get(b).getVoteCount() * eachVotePay);
        logger.debug("pay {} stand reward {}", Hex.toHexString(b.toByteArray()), pay);
        payReward(b.toByteArray(), pay);
      }
    }
  }

  public void payBlockReward(byte[] executiveAddress, long value) {
    logger.debug("pay {} block reward {}", Hex.toHexString(executiveAddress), value);
    payReward(executiveAddress, value);
  }

  public void payTransactionFeeReward(byte[] executiveAddress, long value) {
    logger.debug("pay {} transaction fee reward {}", Hex.toHexString(executiveAddress), value);
    payReward(executiveAddress, value);
  }

  private void payReward(byte[] executiveAddress, long value) {
    long cycle = dynamicPropertiesStore.getCurrentCycleNumber();
    int brokerage = delegationStore.getBrokerage(cycle, executiveAddress);
    double brokerageRate = (double) brokerage / 100;
    long brokerageAmount = (long) (brokerageRate * value);
    value -= brokerageAmount;
    delegationStore.addReward(cycle, executiveAddress, value);
    adjustAllowance(executiveAddress, brokerageAmount);
  }

  public void withdrawReward(byte[] address) {
    if (!dynamicPropertiesStore.allowChangeDelegation()) {
      return;
    }
    AccountCapsule accountCapsule = accountStore.get(address);
    long beginCycle = delegationStore.getBeginCycle(address);
    long endCycle = delegationStore.getEndCycle(address);
    long currentCycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long reward = 0;
    if (beginCycle > currentCycle || accountCapsule == null) {
      return;
    }
    if (beginCycle == currentCycle) {
      AccountCapsule account = delegationStore.getAccountVote(beginCycle, address);
      if (account != null) {
        return;
      }
    }
    //withdraw the latest cycle reward
    if (beginCycle + 1 == endCycle && beginCycle < currentCycle) {
      AccountCapsule account = delegationStore.getAccountVote(beginCycle, address);
      if (account != null) {
        reward = computeReward(beginCycle, endCycle, account);
        adjustAllowance(address, reward);
        reward = 0;
        logger.info("latest cycle reward {},{}", beginCycle, account.getVotesList());
      }
      beginCycle += 1;
    }
    //
    endCycle = currentCycle;
    if (CollectionUtils.isEmpty(accountCapsule.getVotesList())) {
      delegationStore.setBeginCycle(address, endCycle + 1);
      return;
    }
    if (beginCycle < endCycle) {
      reward += computeReward(beginCycle, endCycle, accountCapsule);
      adjustAllowance(address, reward);
    }
    delegationStore.setBeginCycle(address, endCycle);
    delegationStore.setEndCycle(address, endCycle + 1);
    delegationStore.setAccountVote(endCycle, address, accountCapsule);
    logger.info("adjust {} allowance {}, now currentCycle {}, beginCycle {}, endCycle {}, "
            + "account vote {},", Hex.toHexString(address), reward, currentCycle,
        beginCycle, endCycle, accountCapsule.getVotesList());
  }

  public long queryReward(byte[] address) {
    if (!dynamicPropertiesStore.allowChangeDelegation()) {
      return 0;
    }

    AccountCapsule accountCapsule = accountStore.get(address);
    long beginCycle = delegationStore.getBeginCycle(address);
    long endCycle = delegationStore.getEndCycle(address);
    long currentCycle = dynamicPropertiesStore.getCurrentCycleNumber();
    long reward = 0;
    if (accountCapsule == null) {
      return 0;
    }
    if (beginCycle > currentCycle) {
      return accountCapsule.getAllowance();
    }
    //withdraw the latest cycle reward
    if (beginCycle + 1 == endCycle && beginCycle < currentCycle) {
      AccountCapsule account = delegationStore.getAccountVote(beginCycle, address);
      if (account != null) {
        reward = computeReward(beginCycle, endCycle, account);
      }
      beginCycle += 1;
    }
    //
    endCycle = currentCycle;
    if (CollectionUtils.isEmpty(accountCapsule.getVotesList())) {
      return reward + accountCapsule.getAllowance();
    }
    if (beginCycle < endCycle) {
      reward += computeReward(beginCycle, endCycle, accountCapsule);
    }
    return reward + accountCapsule.getAllowance();
  }

  private long computeReward(long cycle, AccountCapsule accountCapsule) {
    long reward = 0;
    for (Vote vote : accountCapsule.getVotesList()) {
      byte[] srAddress = vote.getVoteAddress().toByteArray();
      long totalReward = delegationStore.getReward(cycle, srAddress);
      long totalVote = delegationStore.getExecutiveVote(cycle, srAddress);
      if (totalVote == DelegationStore.REMARK || totalVote == 0) {
        continue;
      }
      long userVote = vote.getVoteCount();
      double voteRate = (double) userVote / totalVote;
      reward += voteRate * totalReward;
      logger.debug("computeReward {} {} {} {},{},{},{}", cycle,
          Hex.toHexString(accountCapsule.getAddress().toByteArray()), Hex.toHexString(srAddress),
          userVote, totalVote, totalReward, reward);
    }
    return reward;
  }

  /**
   * Compute reward from begin cycle to end cycle, which endCycle must greater than beginCycle.
   * While computing reward after new reward algorithm taking effective cycle number,
   * it will use new algorithm instead of old way.
   * @param beginCycle begin cycle (include)
   * @param endCycle end cycle (exclude)
   * @param accountCapsule account capsule
   * @return total reward
   */
  private long computeReward(long beginCycle, long endCycle, AccountCapsule accountCapsule) {
    if (beginCycle >= endCycle) {
      return 0;
    }

    long reward = 0;
    long newAlgorithmCycle = dynamicPropertiesStore.getNewRewardAlgorithmEffectiveCycle();
    if (beginCycle < newAlgorithmCycle) {
      long oldEndCycle = Math.min(endCycle, newAlgorithmCycle);
      for (long cycle = beginCycle; cycle < oldEndCycle; cycle++) {
        reward += computeReward(cycle, accountCapsule);
      }
      beginCycle = oldEndCycle;
    }
    if (beginCycle < endCycle) {
      for (Vote vote : accountCapsule.getVotesList()) {
        byte[] srAddress = vote.getVoteAddress().toByteArray();
        BigInteger beginVi = delegationStore.getExecutiveVi(beginCycle - 1, srAddress);
        BigInteger endVi = delegationStore.getExecutiveVi(endCycle - 1, srAddress);
        BigInteger deltaVi = endVi.subtract(beginVi);
        if (deltaVi.signum() <= 0) {
          continue;
        }
        long userVote = vote.getVoteCount();
        reward += deltaVi.multiply(BigInteger.valueOf(userVote))
            .divide(DelegationStore.DECIMAL_OF_VI_REWARD).longValue();
      }
    }
    return reward;
  }

  public ExecutiveCapsule getExecutiveByAddress(ByteString address) {
    return executiveStore.get(address.toByteArray());
  }

  public void adjustAllowance(byte[] address, long amount) {
    try {
      if (amount <= 0) {
        return;
      }
      adjustAllowance(accountStore, address, amount);
    } catch (BalanceInsufficientException e) {
      logger.error("withdrawReward error: {},{}", Hex.toHexString(address), address, e);
    }
  }

  public void adjustAllowance(AccountStore accountStore, byte[] accountAddress, long amount)
      throws BalanceInsufficientException {
    AccountCapsule account = accountStore.getUnchecked(accountAddress);
    long allowance = account.getAllowance();
    if (amount == 0) {
      return;
    }

    if (amount < 0 && allowance < -amount) {
      throw new BalanceInsufficientException(
          StringUtil.createReadableString(accountAddress) + " insufficient balance");
    }
    account.setAllowance(allowance + amount);
    accountStore.put(account.createDbKey(), account);
  }

  private void sortExecutive(List<ByteString> list) {
    list.sort(Comparator.comparingLong((ByteString b) -> getExecutiveByAddress(b).getVoteCount())
        .reversed().thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));
  }
}
