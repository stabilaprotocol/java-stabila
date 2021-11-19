package org.stabila.core.vm.nativecontract;

import static org.stabila.core.actuator.ActuatorConstant.STORE_NOT_EXIST;
import static org.stabila.core.config.Parameter.ChainConstant.STB_PRECISION;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.stabila.core.vm.repository.Repository;
import org.stabila.common.utils.FastByteComparisons;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.capsule.VotesCapsule;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.vm.config.VMConfig;
import org.stabila.core.vm.nativecontract.param.UncdBalanceParam;
import org.stabila.core.vm.utils.VoteRewardUtil;
import org.stabila.protos.Protocol;

@Slf4j(topic = "Processor")
public class UncdBalanceProcessor {

  public void validate(UncdBalanceParam param, Repository repo)
      throws ContractValidateException {
    if (repo == null) {
      throw new ContractValidateException(STORE_NOT_EXIST);
    }

    byte[] ownerAddress = param.getOwnerAddress();
    AccountCapsule ownerCapsule = repo.getAccount(ownerAddress);
    byte[] receiverAddress = param.getReceiverAddress();
    long now = repo.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
    if (!FastByteComparisons.isEqual(ownerAddress, receiverAddress)) {
      param.setDelegating(true);

      // check if delegated resource exists
      byte[] key = DelegatedResourceCapsule.createDbKey(ownerAddress, receiverAddress);
      DelegatedResourceCapsule delegatedResourceCapsule = repo.getDelegatedResource(key);
      if (delegatedResourceCapsule == null) {
        throw new ContractValidateException("delegated Resource does not exist");
      }

      // validate args @cdedBalance and @expireTime
      switch (param.getResourceType()) {
        case BANDWIDTH:
          // validate cded balance
          if (delegatedResourceCapsule.getCdedBalanceForBandwidth() <= 0) {
            throw new ContractValidateException("no delegatedCdedBalance(BANDWIDTH)");
          }
          // check if it is time to uncd
          if (delegatedResourceCapsule.getExpireTimeForBandwidth() > now) {
            throw new ContractValidateException("It's not time to uncd(BANDWIDTH).");
          }
          break;
        case UCR:
          // validate cded balance
          if (delegatedResourceCapsule.getCdedBalanceForUcr() <= 0) {
            throw new ContractValidateException("no delegateCdedBalance(Ucr)");
          }
          // check if it is time to uncd
          if (delegatedResourceCapsule.getExpireTimeForUcr() > now) {
            throw new ContractValidateException("It's not time to uncd(Ucr).");
          }
          break;
        default:
          throw new ContractValidateException("ResourceCode error."
              + "valid ResourceCode[BANDWIDTH、Ucr]");
      }
    } else {
      switch (param.getResourceType()) {
        case BANDWIDTH:
          // validate cded balance
          if (ownerCapsule.getCdedCount() <= 0) {
            throw new ContractValidateException("no cdedBalance(BANDWIDTH)");
          }
          // check if it is time to uncd
          long allowedUncdCount = ownerCapsule.getCdedList().stream()
              .filter(cded -> cded.getExpireTime() <= now).count();
          if (allowedUncdCount <= 0) {
            throw new ContractValidateException("It's not time to uncd(BANDWIDTH).");
          }
          break;
        case UCR:
          Protocol.Account.Cded cdedForUcr = ownerCapsule.getAccountResource()
              .getCdedBalanceForUcr();
          // validate cded balance
          if (cdedForUcr.getCdedBalance() <= 0) {
            throw new ContractValidateException("no cdedBalance(Ucr)");
          }
          // check if it is time to uncd
          if (cdedForUcr.getExpireTime() > now) {
            throw new ContractValidateException("It's not time to uncd(Ucr).");
          }
          break;
        default:
          throw new ContractValidateException("ResourceCode error."
              + "valid ResourceCode[BANDWIDTH、Ucr]");
      }
    }
  }

  public long execute(UncdBalanceParam param, Repository repo) {
    byte[] ownerAddress = param.getOwnerAddress();
    byte[] receiverAddress = param.getReceiverAddress();

    AccountCapsule accountCapsule = repo.getAccount(ownerAddress);
    long oldBalance = accountCapsule.getBalance();
    long uncdBalance = 0L;

    if (param.isDelegating()) {
      byte[] key = DelegatedResourceCapsule.createDbKey(ownerAddress, receiverAddress);
      DelegatedResourceCapsule delegatedResourceCapsule = repo.getDelegatedResource(key);

      // reset delegated resource and deduce delegated balance
      switch (param.getResourceType()) {
        case BANDWIDTH:
          uncdBalance = delegatedResourceCapsule.getCdedBalanceForBandwidth();
          delegatedResourceCapsule.setCdedBalanceForBandwidth(0, 0);
          accountCapsule.addDelegatedCdedBalanceForBandwidth(-uncdBalance);
          break;
        case UCR:
          uncdBalance = delegatedResourceCapsule.getCdedBalanceForUcr();
          delegatedResourceCapsule.setCdedBalanceForUcr(0, 0);
          accountCapsule.addDelegatedCdedBalanceForUcr(-uncdBalance);
          break;
        default:
          //this should never happen
          break;
      }
      repo.updateDelegatedResource(key, delegatedResourceCapsule);

      // take back resource from receiver account
      AccountCapsule receiverCapsule = repo.getAccount(receiverAddress);
      if (receiverCapsule != null) {
        switch (param.getResourceType()) {
          case BANDWIDTH:
            receiverCapsule.safeAddAcquiredDelegatedCdedBalanceForBandwidth(-uncdBalance);
            break;
          case UCR:
            receiverCapsule.safeAddAcquiredDelegatedCdedBalanceForUcr(-uncdBalance);
            break;
          default:
            //this should never happen
            break;
        }
        repo.updateAccount(receiverCapsule.createDbKey(), receiverCapsule);
      }

      // increase balance of owner
      accountCapsule.setBalance(oldBalance + uncdBalance);
    } else {
      switch (param.getResourceType()) {
        case BANDWIDTH:
          List<Protocol.Account.Cded> cdedList = Lists.newArrayList();
          cdedList.addAll(accountCapsule.getCdedList());
          Iterator<Protocol.Account.Cded> iterator = cdedList.iterator();
          long now = repo.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
          while (iterator.hasNext()) {
            Protocol.Account.Cded next = iterator.next();
            if (next.getExpireTime() <= now) {
              uncdBalance += next.getCdedBalance();
              iterator.remove();
            }
          }
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + uncdBalance)
              .clearCded().addAllCded(cdedList).build());
          break;
        case UCR:
          uncdBalance = accountCapsule.getAccountResource().getCdedBalanceForUcr()
              .getCdedBalance();
          Protocol.Account.AccountResource newAccountResource =
              accountCapsule.getAccountResource().toBuilder()
              .clearCdedBalanceForUcr().build();
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + uncdBalance)
              .setAccountResource(newAccountResource).build());
          break;
        default:
          //this should never happen
          break;
      }

    }

    // adjust total resource, used to be a bug here
    switch (param.getResourceType()) {
      case BANDWIDTH:
        repo.addTotalNetWeight(-uncdBalance / STB_PRECISION);
        break;
      case UCR:
        repo.addTotalUcrWeight(-uncdBalance / STB_PRECISION);
        break;
      default:
        //this should never happen
        break;
    }

    repo.updateAccount(accountCapsule.createDbKey(), accountCapsule);

    if (VMConfig.allowSvmVote() && !accountCapsule.getVotesList().isEmpty()) {
      long usedStabilaPower = 0;
      for (Protocol.Vote vote : accountCapsule.getVotesList()) {
        usedStabilaPower += vote.getVoteCount();
      }
      if (accountCapsule.getStabilaPower() < usedStabilaPower * STB_PRECISION) {
        VoteRewardUtil.withdrawReward(ownerAddress, repo);
        VotesCapsule votesCapsule = repo.getVotes(ownerAddress);
        accountCapsule = repo.getAccount(ownerAddress);
        if (votesCapsule == null) {
          votesCapsule = new VotesCapsule(ByteString.copyFrom(ownerAddress),
              accountCapsule.getVotesList());
        } else {
          votesCapsule.clearNewVotes();
        }
        accountCapsule.clearVotes();
        repo.updateVotes(ownerAddress, votesCapsule);
        repo.updateAccount(ownerAddress, accountCapsule);
      }
    }

    return uncdBalance;
  }
}
