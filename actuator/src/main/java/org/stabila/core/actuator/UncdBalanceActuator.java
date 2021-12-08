package org.stabila.core.actuator;

import static org.stabila.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.stabila.core.config.Parameter.ChainConstant.STB_PRECISION;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.VotesCapsule;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.service.MortgageService;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DelegatedResourceAccountIndexStore;
import org.stabila.core.store.DelegatedResourceStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.VotesStore;
import org.stabila.protos.Protocol.Account.AccountResource;
import org.stabila.protos.Protocol.Account.Cded;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.BalanceContract.UncdBalanceContract;

@Slf4j(topic = "actuator")
public class UncdBalanceActuator extends AbstractActuator {

  public UncdBalanceActuator() {
    super(ContractType.UncdBalanceContract, UncdBalanceContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    final UncdBalanceContract uncdBalanceContract;
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore = chainBaseManager
        .getDelegatedResourceAccountIndexStore();
    VotesStore votesStore = chainBaseManager.getVotesStore();
    MortgageService mortgageService = chainBaseManager.getMortgageService();
    try {
      uncdBalanceContract = any.unpack(UncdBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    byte[] ownerAddress = uncdBalanceContract.getOwnerAddress().toByteArray();

    //
    mortgageService.withdrawReward(ownerAddress);

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    long oldBalance = accountCapsule.getBalance();

    long uncdBalance = 0L;

    if (dynamicStore.supportAllowNewResourceModel()
        && accountCapsule.oldStabilaPowerIsNotInitialized()) {
      accountCapsule.initializeOldStabilaPower();
    }

    byte[] receiverAddress = uncdBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, uncd cded balance for this account.
    //otherwise,uncd delegated cded balance provided this account.
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      byte[] key = DelegatedResourceCapsule
          .createDbKey(uncdBalanceContract.getOwnerAddress().toByteArray(),
              uncdBalanceContract.getReceiverAddress().toByteArray());
      DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
          .get(key);

      switch (uncdBalanceContract.getResource()) {
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

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (dynamicStore.getAllowSvmConstantinople() == 0 ||
          (receiverCapsule != null && receiverCapsule.getType() != AccountType.Contract)) {
        switch (uncdBalanceContract.getResource()) {
          case BANDWIDTH:
            if (dynamicStore.getAllowSvmSolidity059() == 1
                && receiverCapsule.getAcquiredDelegatedCdedBalanceForBandwidth()
                < uncdBalance) {
              receiverCapsule.setAcquiredDelegatedCdedBalanceForBandwidth(0);
            } else {
              receiverCapsule.addAcquiredDelegatedCdedBalanceForBandwidth(-uncdBalance);
            }
            break;
          case UCR:
            if (dynamicStore.getAllowSvmSolidity059() == 1
                && receiverCapsule.getAcquiredDelegatedCdedBalanceForUcr() < uncdBalance) {
              receiverCapsule.setAcquiredDelegatedCdedBalanceForUcr(0);
            } else {
              receiverCapsule.addAcquiredDelegatedCdedBalanceForUcr(-uncdBalance);
            }
            break;
          default:
            //this should never happen
            break;
        }
        accountStore.put(receiverCapsule.createDbKey(), receiverCapsule);
      }

      accountCapsule.setBalance(oldBalance + uncdBalance);

      if (delegatedResourceCapsule.getCdedBalanceForBandwidth() == 0
          && delegatedResourceCapsule.getCdedBalanceForUcr() == 0) {
        delegatedResourceStore.delete(key);

        //modify DelegatedResourceAccountIndexStore
        {
          DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(ownerAddress);
          if (delegatedResourceAccountIndexCapsule != null) {
            List<ByteString> toAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
                .getToAccountsList());
            toAccountsList.remove(ByteString.copyFrom(receiverAddress));
            delegatedResourceAccountIndexCapsule.setAllToAccounts(toAccountsList);
            delegatedResourceAccountIndexStore
                .put(ownerAddress, delegatedResourceAccountIndexCapsule);
          }
        }

        {
          DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
              .get(receiverAddress);
          if (delegatedResourceAccountIndexCapsule != null) {
            List<ByteString> fromAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule
                .getFromAccountsList());
            fromAccountsList.remove(ByteString.copyFrom(ownerAddress));
            delegatedResourceAccountIndexCapsule.setAllFromAccounts(fromAccountsList);
            delegatedResourceAccountIndexStore
                .put(receiverAddress, delegatedResourceAccountIndexCapsule);
          }
        }

      } else {
        delegatedResourceStore.put(key, delegatedResourceCapsule);
      }
    } else {
      switch (uncdBalanceContract.getResource()) {
        case BANDWIDTH:

          List<Cded> cdedList = Lists.newArrayList();
          cdedList.addAll(accountCapsule.getCdedList());
          Iterator<Cded> iterator = cdedList.iterator();
          long now = dynamicStore.getLatestBlockHeaderTimestamp();
          while (iterator.hasNext()) {
            Cded next = iterator.next();
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

          AccountResource newAccountResource = accountCapsule.getAccountResource().toBuilder()
              .clearCdedBalanceForUcr().build();
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + uncdBalance)
              .setAccountResource(newAccountResource).build());
          break;
        case STABILA_POWER:
          uncdBalance = accountCapsule.getStabilaPowerCdedBalance();
          accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
              .setBalance(oldBalance + uncdBalance)
              .clearStabilaPower().build());
          break;
        default:
          //this should never happen
          break;
      }

    }

    switch (uncdBalanceContract.getResource()) {
      case BANDWIDTH:
        dynamicStore
            .addTotalNetWeight(-uncdBalance / STB_PRECISION);
        break;
      case UCR:
        dynamicStore
            .addTotalUcrWeight(-uncdBalance / STB_PRECISION);
        break;
      case STABILA_POWER:
        dynamicStore
            .addTotalStabilaPowerWeight(-uncdBalance / STB_PRECISION);
        break;
      default:
        //this should never happen
        break;
    }

    boolean needToClearVote = true;
    if (dynamicStore.supportAllowNewResourceModel()
        && accountCapsule.oldStabilaPowerIsInvalid()) {
      switch (uncdBalanceContract.getResource()) {
        case BANDWIDTH:
        case UCR:
          needToClearVote = false;
          break;
        default:
          break;
      }
    }

    if (needToClearVote) {
      VotesCapsule votesCapsule;
      if (!votesStore.has(ownerAddress)) {
        votesCapsule = new VotesCapsule(uncdBalanceContract.getOwnerAddress(),
            accountCapsule.getVotesList());
      } else {
        votesCapsule = votesStore.get(ownerAddress);
      }
      accountCapsule.clearVotes();
      votesCapsule.clearNewVotes();
      votesStore.put(ownerAddress, votesCapsule);
    }

    if (dynamicStore.supportAllowNewResourceModel()
        && !accountCapsule.oldStabilaPowerIsInvalid()) {
      accountCapsule.invalidateOldStabilaPower();
    }

    accountStore.put(ownerAddress, accountCapsule);

    ret.setUncdAmount(uncdBalance);
    ret.setStatus(fee, code.SUCESS);

    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    if (!this.any.is(UncdBalanceContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [UncdBalanceContract], real type[" + any
              .getClass() + "]");
    }
    final UncdBalanceContract uncdBalanceContract;
    try {
      uncdBalanceContract = this.any.unpack(UncdBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = uncdBalanceContract.getOwnerAddress().toByteArray();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] does not exist");
    }
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    byte[] receiverAddress = uncdBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is not included in the contract, uncd cded balance for this account.
    //otherwise,uncd delegated cded balance provided this account.
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      if (Arrays.equals(receiverAddress, ownerAddress)) {
        throw new ContractValidateException(
            "receiverAddress must not be the same as ownerAddress");
      }

      if (!DecodeUtil.addressValid(receiverAddress)) {
        throw new ContractValidateException("Invalid receiverAddress");
      }

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (dynamicStore.getAllowSvmConstantinople() == 0
          && receiverCapsule == null) {
        String readableReceiverAddress = StringUtil.createReadableString(receiverAddress);
        throw new ContractValidateException(
            "Receiver Account[" + readableReceiverAddress + "] does not exist");
      }

      byte[] key = DelegatedResourceCapsule
          .createDbKey(uncdBalanceContract.getOwnerAddress().toByteArray(),
              uncdBalanceContract.getReceiverAddress().toByteArray());
      DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
          .get(key);
      if (delegatedResourceCapsule == null) {
        throw new ContractValidateException(
            "delegated Resource does not exist");
      }

      switch (uncdBalanceContract.getResource()) {
        case BANDWIDTH:
          if (delegatedResourceCapsule.getCdedBalanceForBandwidth() <= 0) {
            throw new ContractValidateException("no delegatedCdedBalance(BANDWIDTH)");
          }

          if (dynamicStore.getAllowSvmConstantinople() == 0) {
            if (receiverCapsule.getAcquiredDelegatedCdedBalanceForBandwidth()
                < delegatedResourceCapsule.getCdedBalanceForBandwidth()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedCdedBalanceForBandwidth[" + receiverCapsule
                      .getAcquiredDelegatedCdedBalanceForBandwidth() + "] < delegatedBandwidth["
                      + delegatedResourceCapsule.getCdedBalanceForBandwidth()
                      + "]");
            }
          } else {
            if (dynamicStore.getAllowSvmSolidity059() != 1
                && receiverCapsule != null
                && receiverCapsule.getType() != AccountType.Contract
                && receiverCapsule.getAcquiredDelegatedCdedBalanceForBandwidth()
                < delegatedResourceCapsule.getCdedBalanceForBandwidth()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedCdedBalanceForBandwidth[" + receiverCapsule
                      .getAcquiredDelegatedCdedBalanceForBandwidth() + "] < delegatedBandwidth["
                      + delegatedResourceCapsule.getCdedBalanceForBandwidth()
                      + "]");
            }
          }

          if (delegatedResourceCapsule.getExpireTimeForBandwidth() > now) {
            throw new ContractValidateException("It's not time to uncd.");
          }
          break;
        case UCR:
          if (delegatedResourceCapsule.getCdedBalanceForUcr() <= 0) {
            throw new ContractValidateException("no delegateCdedBalance(Ucr)");
          }
          if (dynamicStore.getAllowSvmConstantinople() == 0) {
            if (receiverCapsule.getAcquiredDelegatedCdedBalanceForUcr()
                < delegatedResourceCapsule.getCdedBalanceForUcr()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedCdedBalanceForUcr[" + receiverCapsule
                      .getAcquiredDelegatedCdedBalanceForUcr() + "] < delegatedUcr["
                      + delegatedResourceCapsule.getCdedBalanceForUcr() +
                      "]");
            }
          } else {
            if (dynamicStore.getAllowSvmSolidity059() != 1
                && receiverCapsule != null
                && receiverCapsule.getType() != AccountType.Contract
                && receiverCapsule.getAcquiredDelegatedCdedBalanceForUcr()
                < delegatedResourceCapsule.getCdedBalanceForUcr()) {
              throw new ContractValidateException(
                  "AcquiredDelegatedCdedBalanceForUcr[" + receiverCapsule
                      .getAcquiredDelegatedCdedBalanceForUcr() + "] < delegatedUcr["
                      + delegatedResourceCapsule.getCdedBalanceForUcr() +
                      "]");
            }
          }

          if (delegatedResourceCapsule.getExpireTimeForUcr(dynamicStore) > now) {
            throw new ContractValidateException("It's not time to uncd.");
          }
          break;
        default:
          throw new ContractValidateException(
              "ResourceCode error.valid ResourceCode[BANDWIDTH、Ucr]");
      }

    } else {
      switch (uncdBalanceContract.getResource()) {
        case BANDWIDTH:
          if (accountCapsule.getCdedCount() <= 0) {
            throw new ContractValidateException("no cdedBalance(BANDWIDTH)");
          }

          long allowedUncdCount = accountCapsule.getCdedList().stream()
              .filter(cded -> cded.getExpireTime() <= now).count();
          if (allowedUncdCount <= 0) {
            throw new ContractValidateException("It's not time to uncd(BANDWIDTH).");
          }
          break;
        case UCR:
          Cded cdedBalanceForUcr = accountCapsule.getAccountResource()
              .getCdedBalanceForUcr();
          if (cdedBalanceForUcr.getCdedBalance() <= 0) {
            throw new ContractValidateException("no cdedBalance(Ucr)");
          }
          if (cdedBalanceForUcr.getExpireTime() > now) {
            throw new ContractValidateException("It's not time to uncd(Ucr).");
          }

          break;
        case STABILA_POWER:
          if (dynamicStore.supportAllowNewResourceModel()) {
            Cded cdedBalanceForStabilaPower = accountCapsule.getInstance().getStabilaPower();
            if (cdedBalanceForStabilaPower.getCdedBalance() <= 0) {
              throw new ContractValidateException("no cdedBalance(StabilaPower)");
            }
            if (cdedBalanceForStabilaPower.getExpireTime() > now) {
              throw new ContractValidateException("It's not time to uncd(StabilaPower).");
            }
          } else {
            throw new ContractValidateException(
                "ResourceCode error.valid ResourceCode[BANDWIDTH、Ucr]");
          }
          break;
        default:
          if (dynamicStore.supportAllowNewResourceModel()) {
            throw new ContractValidateException(
                "ResourceCode error.valid ResourceCode[BANDWIDTH、Ucr、STABILA_POWER]");
          } else {
            throw new ContractValidateException(
                "ResourceCode error.valid ResourceCode[BANDWIDTH、Ucr]");
          }
      }

    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(UncdBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
