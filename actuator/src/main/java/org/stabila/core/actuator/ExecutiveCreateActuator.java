package org.stabila.core.actuator;

import static org.stabila.core.actuator.ActuatorConstant.EXECUTIVE_EXCEPTION_STR;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.stabila.core.utils.TransactionUtil;
import org.stabila.common.utils.Commons;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.exception.BalanceInsufficientException;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.ExecutiveStore;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.ExecutiveContract.ExecutiveCreateContract;

@Slf4j(topic = "actuator")
public class ExecutiveCreateActuator extends AbstractActuator {

  public ExecutiveCreateActuator() {
    super(ContractType.ExecutiveCreateContract, ExecutiveCreateContract.class);
  }

  @Override
  public boolean execute(Object object) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) object;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    try {
      final ExecutiveCreateContract executiveCreateContract = this.any
          .unpack(ExecutiveCreateContract.class);
      this.createExecutive(executiveCreateContract);
      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
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
    ExecutiveStore executiveStore = chainBaseManager.getExecutiveStore();
    if (!this.any.is(ExecutiveCreateContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [ExecutiveCreateContract],real type[" + any
              .getClass() + "]");
    }
    final ExecutiveCreateContract contract;
    try {
      contract = this.any.unpack(ExecutiveCreateContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    if (!TransactionUtil.validUrl(contract.getUrl().toByteArray())) {
      throw new ContractValidateException("Invalid url");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);

    if (accountCapsule == null) {
      throw new ContractValidateException("account[" + readableOwnerAddress
          + ActuatorConstant.NOT_EXIST_STR);
    }
    /* todo later
    if (ArrayUtils.isEmpty(accountCapsule.getAccountName().toByteArray())) {
      throw new ContractValidateException("accountStore name not set");
    } */

    if (executiveStore.has(ownerAddress)) {
      throw new ContractValidateException(
          EXECUTIVE_EXCEPTION_STR + readableOwnerAddress + "] has existed");
    }

    if (accountCapsule.getBalance() < dynamicStore
        .getAccountUpgradeCost()) {
      throw new ContractValidateException("balance < AccountUpgradeCost");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(ExecutiveCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return chainBaseManager.getDynamicPropertiesStore().getAccountUpgradeCost();
  }

  private void createExecutive(final ExecutiveCreateContract executiveCreateContract)
      throws BalanceInsufficientException {
    AccountStore accountStore = chainBaseManager.getAccountStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    ExecutiveStore executiveStore = chainBaseManager.getExecutiveStore();
    //Create Executive by executiveCreateContract
    final ExecutiveCapsule executiveCapsule = new ExecutiveCapsule(
        executiveCreateContract.getOwnerAddress(),
        0,
        executiveCreateContract.getUrl().toStringUtf8());

    logger.debug("createExecutive,address[{}]", executiveCapsule.createReadableString());
    executiveStore.put(executiveCapsule.createDbKey(), executiveCapsule);
    AccountCapsule accountCapsule = accountStore
        .get(executiveCapsule.createDbKey());
    accountCapsule.setIsExecutive(true);
    if (dynamicStore.getAllowMultiSign() == 1) {
      accountCapsule.setDefaultExecutivePermission(dynamicStore);
    }
    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    long cost = dynamicStore.getAccountUpgradeCost();
    Commons
        .adjustBalance(accountStore, executiveCreateContract.getOwnerAddress().toByteArray(), -cost);
    if (dynamicStore.supportBlackHoleOptimization()) {
      dynamicStore.burnStb(cost);
    } else {
      Commons.adjustBalance(accountStore, accountStore.getUnit(), +cost);
    }
    dynamicStore.addTotalCreateExecutiveCost(cost);
  }
}
