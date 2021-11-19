package org.stabila.core.vm.nativecontract;

import static org.stabila.core.actuator.ActuatorConstant.STORE_NOT_EXIST;
import static org.stabila.core.config.Parameter.ChainConstant.CDED_PERIOD;
import static org.stabila.core.config.Parameter.ChainConstant.STB_PRECISION;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.stabila.common.utils.FastByteComparisons;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.vm.nativecontract.param.CdBalanceParam;
import org.stabila.core.vm.repository.Repository;
import org.stabila.protos.Protocol;

@Slf4j(topic = "Processor")
public class CdBalanceProcessor {

  public void validate(CdBalanceParam param, Repository repo) throws ContractValidateException {
    if (repo == null) {
      throw new ContractValidateException(STORE_NOT_EXIST);
    }

    // validate arg @cdedBalance
    byte[] ownerAddress = param.getOwnerAddress();
    AccountCapsule ownerCapsule = repo.getAccount(ownerAddress);
    long cdedBalance = param.getCdedBalance();
    if (cdedBalance <= 0) {
      throw new ContractValidateException("CdedBalance must be positive");
    } else if (cdedBalance < STB_PRECISION) {
      throw new ContractValidateException("CdedBalance must be more than 1STB");
    } else if (cdedBalance > ownerCapsule.getBalance()) {
      throw new ContractValidateException("CdedBalance must be less than accountBalance");
    }

    // validate cded count of owner account
    int cdedCount = ownerCapsule.getCdedCount();
    if (cdedCount != 0 && cdedCount != 1) {
      throw new ContractValidateException("CdedCount must be 0 or 1");
    }

    // validate arg @resourceType
    switch (param.getResourceType()) {
      case BANDWIDTH:
      case UCR:
        break;
      default:
        throw new ContractValidateException(
            "ResourceCode error,valid ResourceCode[BANDWIDTH、UCR]");
    }

    // validate for delegating resource
    byte[] receiverAddress = param.getReceiverAddress();
    if (!FastByteComparisons.isEqual(ownerAddress, receiverAddress)) {
      param.setDelegating(true);

      // check if receiver account exists. if not, then create a new account
      AccountCapsule receiverCapsule = repo.getAccount(receiverAddress);
      if (receiverCapsule == null) {
        receiverCapsule = repo.createNormalAccount(receiverAddress);
      }

      // forbid delegating resource to contract account
      if (receiverCapsule.getType() == Protocol.AccountType.Contract) {
        throw new ContractValidateException(
            "Do not allow delegate resources to contract addresses");
      }
    }
  }

  public void execute(CdBalanceParam param,  Repository repo) {
    // calculate expire time
    DynamicPropertiesStore dynamicStore = repo.getDynamicPropertiesStore();
    long nowInMs = dynamicStore.getLatestBlockHeaderTimestamp();
    long expireTime = nowInMs + param.getCdedDuration() * CDED_PERIOD;

    byte[] ownerAddress = param.getOwnerAddress();
    byte[] receiverAddress = param.getReceiverAddress();
    long cdedBalance = param.getCdedBalance();
    AccountCapsule accountCapsule = repo.getAccount(ownerAddress);
    // acquire or delegate resource
    if (param.isDelegating()) { // delegate resource
      switch (param.getResourceType()) {
        case BANDWIDTH:
          delegateResource(ownerAddress, receiverAddress,
              cdedBalance, expireTime, true, repo);
          accountCapsule.addDelegatedCdedBalanceForBandwidth(cdedBalance);
          break;
        case UCR:
          delegateResource(ownerAddress, receiverAddress,
              cdedBalance, expireTime, false, repo);
          accountCapsule.addDelegatedCdedBalanceForUcr(cdedBalance);
          break;
        default:
          logger.debug("Resource Code Error.");
      }
    } else { // acquire resource
      switch (param.getResourceType()) {
        case BANDWIDTH:
          accountCapsule.setCdedForBandwidth(
              cdedBalance + accountCapsule.getCdedBalance(),
              expireTime);
          break;
        case UCR:
          accountCapsule.setCdedForUcr(
              cdedBalance + accountCapsule.getAccountResource()
                  .getCdedBalanceForUcr()
                  .getCdedBalance(),
              expireTime);
          break;
        default:
          logger.debug("Resource Code Error.");
      }
    }

    // adjust total resource
    switch (param.getResourceType()) {
      case BANDWIDTH:
        repo.addTotalNetWeight(cdedBalance / STB_PRECISION);
        break;
      case UCR:
        repo.addTotalUcrWeight(cdedBalance / STB_PRECISION);
        break;
      default:
        //this should never happen
        break;
    }

    // deduce balance of owner account
    long newBalance = accountCapsule.getBalance() - cdedBalance;
    accountCapsule.setBalance(newBalance);
    repo.updateAccount(accountCapsule.createDbKey(), accountCapsule);
  }

  private void delegateResource(
      byte[] ownerAddress,
      byte[] receiverAddress,
      long cdedBalance,
      long expireTime,
      boolean isBandwidth,
      Repository repo) {
    byte[] key = DelegatedResourceCapsule.createDbKey(ownerAddress, receiverAddress);

    // insert or update DelegateResource
    DelegatedResourceCapsule delegatedResourceCapsule = repo.getDelegatedResource(key);
    if (delegatedResourceCapsule == null) {
      delegatedResourceCapsule = new DelegatedResourceCapsule(
          ByteString.copyFrom(ownerAddress),
          ByteString.copyFrom(receiverAddress));
    }
    if (isBandwidth) {
      delegatedResourceCapsule.addCdedBalanceForBandwidth(cdedBalance, expireTime);
    } else {
      delegatedResourceCapsule.addCdedBalanceForUcr(cdedBalance, expireTime);
    }
    repo.updateDelegatedResource(key, delegatedResourceCapsule);

    // do delegating resource to receiver account
    AccountCapsule receiverCapsule = repo.getAccount(receiverAddress);
    if (isBandwidth) {
      receiverCapsule.addAcquiredDelegatedCdedBalanceForBandwidth(cdedBalance);
    } else {
      receiverCapsule.addAcquiredDelegatedCdedBalanceForUcr(cdedBalance);
    }
    repo.updateAccount(receiverCapsule.createDbKey(), receiverCapsule);
  }
}
