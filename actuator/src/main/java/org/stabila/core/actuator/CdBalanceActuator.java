package org.stabila.core.actuator;

import static org.stabila.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.stabila.core.config.Parameter.ChainConstant.CDED_PERIOD;
import static org.stabila.core.config.Parameter.ChainConstant.STB_PRECISION;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DelegatedResourceAccountIndexStore;
import org.stabila.core.store.DelegatedResourceStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.BalanceContract.CdBalanceContract;

@Slf4j(topic = "actuator")
public class CdBalanceActuator extends AbstractActuator {

  public CdBalanceActuator() {
    super(ContractType.CdBalanceContract, CdBalanceContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    final CdBalanceContract cdBalanceContract;
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    try {
      cdBalanceContract = any.unpack(CdBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    AccountCapsule accountCapsule = accountStore
        .get(cdBalanceContract.getOwnerAddress().toByteArray());

    if (dynamicStore.supportAllowNewResourceModel()
        && accountCapsule.oldStabilaPowerIsNotInitialized()) {
      accountCapsule.initializeOldStabilaPower();
    }

    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    long duration = cdBalanceContract.getCdedDuration() * CDED_PERIOD;

    long newBalance = accountCapsule.getBalance() - cdBalanceContract.getCdedBalance();

    long cdedBalance = cdBalanceContract.getCdedBalance();
    long expireTime = now + duration;
    byte[] ownerAddress = cdBalanceContract.getOwnerAddress().toByteArray();
    byte[] receiverAddress = cdBalanceContract.getReceiverAddress().toByteArray();

    switch (cdBalanceContract.getResource()) {
      case BANDWIDTH:
        if (!ArrayUtils.isEmpty(receiverAddress)
            && dynamicStore.supportDR()) {
          delegateResource(ownerAddress, receiverAddress, true,
              cdedBalance, expireTime);
          accountCapsule.addDelegatedCdedBalanceForBandwidth(cdedBalance);
        } else {
          long newCdedBalanceForBandwidth =
              cdedBalance + accountCapsule.getCdedBalance();
          accountCapsule.setCdedForBandwidth(newCdedBalanceForBandwidth, expireTime);
        }
        dynamicStore
            .addTotalNetWeight(cdedBalance / STB_PRECISION);
        break;
      case UCR:
        if (!ArrayUtils.isEmpty(receiverAddress)
            && dynamicStore.supportDR()) {
          delegateResource(ownerAddress, receiverAddress, false,
              cdedBalance, expireTime);
          accountCapsule.addDelegatedCdedBalanceForUcr(cdedBalance);
        } else {
          long newCdedBalanceForUcr =
              cdedBalance + accountCapsule.getAccountResource()
                  .getCdedBalanceForUcr()
                  .getCdedBalance();
          accountCapsule.setCdedForUcr(newCdedBalanceForUcr, expireTime);
        }
        dynamicStore
            .addTotalUcrWeight(cdedBalance / STB_PRECISION);
        break;
      case STABILA_POWER:
        long newCdedBalanceForStabilaPower =
            cdedBalance + accountCapsule.getStabilaPowerCdedBalance();
        accountCapsule.setCdedForStabilaPower(newCdedBalanceForStabilaPower, expireTime);

        dynamicStore
            .addTotalStabilaPowerWeight(cdedBalance / STB_PRECISION);
        break;
      default:
        logger.debug("Resource Code Error.");
    }

    accountCapsule.setBalance(newBalance);
    accountStore.put(accountCapsule.createDbKey(), accountCapsule);

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
    if (!any.is(CdBalanceContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [CdBalanceContract],real type[" + any
              .getClass() + "]");
    }

    final CdBalanceContract cdBalanceContract;
    try {
      cdBalanceContract = this.any.unpack(CdBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = cdBalanceContract.getOwnerAddress().toByteArray();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          ActuatorConstant.ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    long cdedBalance = cdBalanceContract.getCdedBalance();
    if (cdedBalance <= 0) {
      throw new ContractValidateException("cdedBalance must be positive");
    }
    if (cdedBalance < STB_PRECISION) {
      throw new ContractValidateException("cdedBalance must be more than 1STB");
    }

    int cdedCount = accountCapsule.getCdedCount();
    if (!(cdedCount == 0 || cdedCount == 1)) {
      throw new ContractValidateException("cdedCount must be 0 or 1");
    }
    if (cdedBalance > accountCapsule.getBalance()) {
      throw new ContractValidateException("cdedBalance must be less than accountBalance");
    }

//    long maxCdedNumber = dbManager.getDynamicPropertiesStore().getMaxCdedNumber();
//    if (accountCapsule.getCdedCount() >= maxCdedNumber) {
//      throw new ContractValidateException("max cded number is: " + maxCdedNumber);
//    }

    long cdedDuration = cdBalanceContract.getCdedDuration();
    long minCdedTime = dynamicStore.getMinCdedTime();
    long maxCdedTime = dynamicStore.getMaxCdedTime();

    boolean needCheckFrozeTime = CommonParameter.getInstance()
        .getCheckCdedTime() == 1;//for test
    if (needCheckFrozeTime && !(cdedDuration >= minCdedTime
        && cdedDuration <= maxCdedTime)) {
      throw new ContractValidateException(
          "cdedDuration must be less than " + maxCdedTime + " days "
              + "and more than " + minCdedTime + " days");
    }

    switch (cdBalanceContract.getResource()) {
      case BANDWIDTH:
      case UCR:
        break;
      case STABILA_POWER:
        if (dynamicStore.supportAllowNewResourceModel()) {
          byte[] receiverAddress = cdBalanceContract.getReceiverAddress().toByteArray();
          if (!ArrayUtils.isEmpty(receiverAddress)) {
            throw new ContractValidateException(
                "STABILA_POWER is not allowed to delegate to other accounts.");
          }
        } else {
          throw new ContractValidateException(
              "ResourceCode error, valid ResourceCode[BANDWIDTH、UCR]");
        }
        break;
      default:
        if (dynamicStore.supportAllowNewResourceModel()) {
          throw new ContractValidateException(
              "ResourceCode error, valid ResourceCode[BANDWIDTH、UCR、STABILA_POWER]");
        } else {
          throw new ContractValidateException(
              "ResourceCode error, valid ResourceCode[BANDWIDTH、UCR]");
        }
    }

    //todo：need version control and config for delegating resource
    byte[] receiverAddress = cdBalanceContract.getReceiverAddress().toByteArray();
    //If the receiver is included in the contract, the receiver will receive the resource.
    if (!ArrayUtils.isEmpty(receiverAddress) && dynamicStore.supportDR()) {
      if (Arrays.equals(receiverAddress, ownerAddress)) {
        throw new ContractValidateException(
            "receiverAddress must not be the same as ownerAddress");
      }

      if (!DecodeUtil.addressValid(receiverAddress)) {
        throw new ContractValidateException("Invalid receiverAddress");
      }

      AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
      if (receiverCapsule == null) {
        String readableOwnerAddress = StringUtil.createReadableString(receiverAddress);
        throw new ContractValidateException(
            ActuatorConstant.ACCOUNT_EXCEPTION_STR
                + readableOwnerAddress + NOT_EXIST_STR);
      }

      if (dynamicStore.getAllowSvmConstantinople() == 1
          && receiverCapsule.getType() == AccountType.Contract) {
        throw new ContractValidateException(
            "Do not allow delegate resources to contract addresses");

      }

    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(CdBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

  private void delegateResource(byte[] ownerAddress, byte[] receiverAddress, boolean isBandwidth,
      long balance, long expireTime) {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DelegatedResourceStore delegatedResourceStore = chainBaseManager.getDelegatedResourceStore();
    DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore = chainBaseManager
        .getDelegatedResourceAccountIndexStore();
    byte[] key = DelegatedResourceCapsule.createDbKey(ownerAddress, receiverAddress);
    //modify DelegatedResourceStore
    DelegatedResourceCapsule delegatedResourceCapsule = delegatedResourceStore
        .get(key);
    if (delegatedResourceCapsule != null) {
      if (isBandwidth) {
        delegatedResourceCapsule.addCdedBalanceForBandwidth(balance, expireTime);
      } else {
        delegatedResourceCapsule.addCdedBalanceForUcr(balance, expireTime);
      }
    } else {
      delegatedResourceCapsule = new DelegatedResourceCapsule(
          ByteString.copyFrom(ownerAddress),
          ByteString.copyFrom(receiverAddress));
      if (isBandwidth) {
        delegatedResourceCapsule.setCdedBalanceForBandwidth(balance, expireTime);
      } else {
        delegatedResourceCapsule.setCdedBalanceForUcr(balance, expireTime);
      }

    }
    delegatedResourceStore.put(key, delegatedResourceCapsule);

    //modify DelegatedResourceAccountIndexStore
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
          .get(ownerAddress);
      if (delegatedResourceAccountIndexCapsule == null) {
        delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(
            ByteString.copyFrom(ownerAddress));
      }
      List<ByteString> toAccountsList = delegatedResourceAccountIndexCapsule.getToAccountsList();
      if (!toAccountsList.contains(ByteString.copyFrom(receiverAddress))) {
        delegatedResourceAccountIndexCapsule.addToAccount(ByteString.copyFrom(receiverAddress));
      }
      delegatedResourceAccountIndexStore
          .put(ownerAddress, delegatedResourceAccountIndexCapsule);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = delegatedResourceAccountIndexStore
          .get(receiverAddress);
      if (delegatedResourceAccountIndexCapsule == null) {
        delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(
            ByteString.copyFrom(receiverAddress));
      }
      List<ByteString> fromAccountsList = delegatedResourceAccountIndexCapsule
          .getFromAccountsList();
      if (!fromAccountsList.contains(ByteString.copyFrom(ownerAddress))) {
        delegatedResourceAccountIndexCapsule.addFromAccount(ByteString.copyFrom(ownerAddress));
      }
      delegatedResourceAccountIndexStore
          .put(receiverAddress, delegatedResourceAccountIndexCapsule);
    }

    //modify AccountStore
    AccountCapsule receiverCapsule = accountStore.get(receiverAddress);
    if (isBandwidth) {
      receiverCapsule.addAcquiredDelegatedCdedBalanceForBandwidth(balance);
    } else {
      receiverCapsule.addAcquiredDelegatedCdedBalanceForUcr(balance);
    }

    accountStore.put(receiverCapsule.createDbKey(), receiverCapsule);
  }

}
