package org.stabila.core.actuator;

import static org.stabila.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.stabila.core.actuator.ActuatorConstant.NOT_EXIST_STR;
import static org.stabila.core.actuator.ActuatorConstant.EXECUTIVE_EXCEPTION_STR;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.stabila.core.utils.ProposalUtil;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.ProposalCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.ProposalContract.ProposalCreateContract;

@Slf4j(topic = "actuator")
public class ProposalCreateActuator extends AbstractActuator {

  public ProposalCreateActuator() {
    super(ContractType.ProposalCreateContract, ProposalCreateContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();

    try {
      final ProposalCreateContract proposalCreateContract = this.any
          .unpack(ProposalCreateContract.class);
      long id = chainBaseManager.getDynamicPropertiesStore().getLatestProposalNum() + 1;
      ProposalCapsule proposalCapsule =
          new ProposalCapsule(proposalCreateContract.getOwnerAddress(), id);

      proposalCapsule.setParameters(proposalCreateContract.getParametersMap());

      long now = chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
      long maintenanceTimeInterval = chainBaseManager.getDynamicPropertiesStore()
          .getMaintenanceTimeInterval();
      proposalCapsule.setCreateTime(now);

      long currentMaintenanceTime =
          chainBaseManager.getDynamicPropertiesStore().getNextMaintenanceTime();
      long now3 = now + CommonParameter.getInstance().getProposalExpireTime();
      long round = (now3 - currentMaintenanceTime) / maintenanceTimeInterval;
      long expirationTime =
          currentMaintenanceTime + (round + 1) * maintenanceTimeInterval;
      proposalCapsule.setExpirationTime(expirationTime);

      chainBaseManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
      chainBaseManager.getDynamicPropertiesStore().saveLatestProposalNum(id);

      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
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
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.any.is(ProposalCreateContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ProposalCreateContract],real type[" + any
              .getClass() + "]");
    }
    final ProposalCreateContract contract;
    try {
      contract = this.any.unpack(ProposalCreateContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    if (!chainBaseManager.getAccountStore().has(ownerAddress)) {
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    if (!chainBaseManager.getExecutiveStore().has(ownerAddress)) {
      throw new ContractValidateException(
          EXECUTIVE_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
    }

    if (contract.getParametersMap().size() == 0) {
      throw new ContractValidateException("This proposal has no parameter.");
    }

    for (Map.Entry<Long, Long> entry : contract.getParametersMap().entrySet()) {
      validateValue(entry);
    }

    validateValues(contract.getParametersMap());

    return true;
  }

  private void validateValue(Map.Entry<Long, Long> entry) throws ContractValidateException {
    ProposalUtil
        .validator(chainBaseManager.getDynamicPropertiesStore(), forkController, entry.getKey(),
            entry.getValue());
  }

  private void validateValues(Map<Long, Long> entries) throws ContractValidateException {
    if (entries.containsKey(ProposalUtil.ProposalType.MAX_ACTIVE_EXECUTIVE_NUM.getCode())
            || entries.containsKey(ProposalUtil.ProposalType.EXECUTIVE_STANDBY_LENGTH.getCode())) {
      long maxActiveExecutiveNum = entries.containsKey(ProposalUtil.ProposalType.MAX_ACTIVE_EXECUTIVE_NUM.getCode())
              ? entries.get(ProposalUtil.ProposalType.MAX_ACTIVE_EXECUTIVE_NUM.getCode())
              : chainBaseManager.getDynamicPropertiesStore().getMaxActiveExecutiveNum();
      long executiveStandbyLength = entries.containsKey(ProposalUtil.ProposalType.EXECUTIVE_STANDBY_LENGTH.getCode())
              ? entries.get(ProposalUtil.ProposalType.EXECUTIVE_STANDBY_LENGTH.getCode())
              : chainBaseManager.getDynamicPropertiesStore().getExecutiveStandbyLength();
      if (maxActiveExecutiveNum > executiveStandbyLength) {
        throw new ContractValidateException("Executive Standby Length can't be less than Max Active Executive Num. " +
                "Both properties can be updated by 1 proposal or these properties can be updates separately according to this rule.");
      }
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(ProposalCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
