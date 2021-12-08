package org.stabila.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DelegationStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.ExecutiveStore;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.StorageContract.UpdateBrokerageContract;

@Slf4j(topic = "actuator")
public class UpdateBrokerageActuator extends AbstractActuator {

  public UpdateBrokerageActuator() {
    super(ContractType.UpdateBrokerageContract, UpdateBrokerageContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    final UpdateBrokerageContract updateBrokerageContract;
    final long fee = calcFee();

    DelegationStore delegationStore = chainBaseManager.getDelegationStore();

    try {
      updateBrokerageContract = any.unpack(UpdateBrokerageContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    byte[] ownerAddress = updateBrokerageContract.getOwnerAddress().toByteArray();
    int brokerage = updateBrokerageContract.getBrokerage();

    delegationStore.setBrokerage(ownerAddress, brokerage);
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
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    AccountStore accountStore = chainBaseManager.getAccountStore();
    ExecutiveStore executiveStore = chainBaseManager.getExecutiveStore();
    if (!dynamicStore.allowChangeDelegation()) {
      throw new ContractValidateException(
          "contract type error, unexpected type [UpdateBrokerageContract]");
    }

    if (!this.any.is(UpdateBrokerageContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [UpdateBrokerageContract], real type[" + any
              .getClass() + "]");
    }
    final UpdateBrokerageContract updateBrokerageContract;
    try {
      updateBrokerageContract = any.unpack(UpdateBrokerageContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = updateBrokerageContract.getOwnerAddress().toByteArray();
    int brokerage = updateBrokerageContract.getBrokerage();

    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }

    if (brokerage < 0 || brokerage > ActuatorConstant.ONE_HUNDRED) {
      throw new ContractValidateException("Invalid brokerage");
    }

    ExecutiveCapsule executiveCapsule = executiveStore.get(ownerAddress);
    if (executiveCapsule == null) {
      throw new ContractValidateException("Not existed executive:" + Hex.toHexString(ownerAddress));
    }

    AccountCapsule account = accountStore.get(ownerAddress);
    if (account == null) {
      throw new ContractValidateException("Account does not exist");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(UpdateBrokerageContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
