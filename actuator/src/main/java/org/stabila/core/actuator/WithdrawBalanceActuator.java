package org.stabila.core.actuator;

import static org.stabila.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.stabila.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.stabila.core.config.Parameter.ChainConstant.CDED_PERIOD;

import com.google.common.math.LongMath;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Arrays;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.stabila.common.utils.Commons;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.exception.BalanceInsufficientException;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.service.MortgageService;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.BalanceContract.WithdrawBalanceContract;

@Slf4j(topic = "actuator")
public class WithdrawBalanceActuator extends AbstractActuator {

  public WithdrawBalanceActuator() {
    super(ContractType.WithdrawBalanceContract, WithdrawBalanceContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    final WithdrawBalanceContract withdrawBalanceContract;
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    MortgageService mortgageService = chainBaseManager.getMortgageService();
    try {
      withdrawBalanceContract = any.unpack(WithdrawBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    mortgageService.withdrawReward(withdrawBalanceContract.getOwnerAddress()
        .toByteArray());

    AccountCapsule accountCapsule = accountStore.
        get(withdrawBalanceContract.getOwnerAddress().toByteArray());
    long oldBalance = accountCapsule.getBalance();
    long allowance = accountCapsule.getAllowance();

    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    try {
      Commons.adjustBalance(accountStore, accountStore.getStabila(), -allowance);
    } catch (BalanceInsufficientException e) {
      throw new ContractExeException(
              "Adjusting genesis account balance failed");
    }
    accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
        .setBalance(oldBalance + allowance)
        .setAllowance(0L)
        .setLatestWithdrawTime(now)
        .build());
    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    ret.setWithdrawAmount(allowance);
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
    MortgageService mortgageService = chainBaseManager.getMortgageService();
    if (!this.any.is(WithdrawBalanceContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [WithdrawBalanceContract], real type[" + any
              .getClass() + "]");
    }
    final WithdrawBalanceContract withdrawBalanceContract;
    try {
      withdrawBalanceContract = this.any.unpack(WithdrawBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = withdrawBalanceContract.getOwnerAddress().toByteArray();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    //boolean isGP = CommonParameter.getInstance()
    //    .getGenesisBlock().getExecutives().stream().anyMatch(executive ->
    //        Arrays.equals(ownerAddress, executive.getAddress()));
    boolean isGP = chainBaseManager.getExecutiveStore().getAllExecutives().stream().filter(ExecutiveCapsule::getIsJobs).anyMatch(wc -> Arrays.equals(wc.getAddress().toByteArray(), ownerAddress));
    if (isGP) {
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress
              + "] is a guard representative and is not allowed to withdraw Balance");
    }

    long latestWithdrawTime = accountCapsule.getLatestWithdrawTime();
    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    long executiveAllowanceCdedTime = dynamicStore.getExecutiveAllowanceCdedTime() * CDED_PERIOD;

    if (now - latestWithdrawTime < executiveAllowanceCdedTime) {
      throw new ContractValidateException("The last withdraw time is "
          + latestWithdrawTime + ", less than 24 hours");
    }

    if (accountCapsule.getAllowance() <= 0 &&
        mortgageService.queryReward(ownerAddress) <= 0) {
      throw new ContractValidateException("executiveAccount does not have any reward");
    }
    try {
      LongMath.checkedAdd(accountCapsule.getBalance(), accountCapsule.getAllowance());
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(WithdrawBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
