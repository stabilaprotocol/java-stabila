package org.stabila.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.ProposalCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.ItemNotFoundException;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.ProposalStore;
import org.stabila.core.store.ExecutiveStore;
import org.stabila.protos.Protocol.Proposal.State;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.ProposalContract.ProposalApproveContract;

@Slf4j(topic = "actuator")
public class ProposalApproveActuator extends AbstractActuator {

  public ProposalApproveActuator() {
    super(ContractType.ProposalApproveContract, ProposalApproveContract.class);
  }

  @Override
  public boolean execute(Object result) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) result;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    ProposalStore proposalStore = chainBaseManager.getProposalStore();
    try {
      final ProposalApproveContract proposalApproveContract =
          this.any.unpack(ProposalApproveContract.class);
      ProposalCapsule proposalCapsule = proposalStore
          .get(ByteArray.fromLong(proposalApproveContract.getProposalId()));
      ByteString committeeAddress = proposalApproveContract.getOwnerAddress();
      if (proposalApproveContract.getIsAddApproval()) {
        proposalCapsule.addApproval(committeeAddress);
      } else {
        proposalCapsule.removeApproval(committeeAddress);
      }
      proposalStore.put(proposalCapsule.createDbKey(), proposalCapsule);

      ret.setStatus(fee, code.SUCESS);
    } catch (ItemNotFoundException | InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (Objects.isNull(this.any)) {
      throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
    }
    if (Objects.isNull(chainBaseManager)) {
      throw new ContractValidateException(ActuatorConstant.STORE_NOT_EXIST);
    }
    AccountStore accountStore = chainBaseManager.getAccountStore();
    ExecutiveStore executiveStore = chainBaseManager.getExecutiveStore();
    ProposalStore proposalStore = chainBaseManager.getProposalStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    if (!this.any.is(ProposalApproveContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [ProposalApproveContract],real type[" + any
              .getClass() + "]");
    }
    final ProposalApproveContract contract;
    try {
      contract = this.any.unpack(ProposalApproveContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    if (!accountStore.has(ownerAddress)) {
      throw new ContractValidateException(ActuatorConstant.ACCOUNT_EXCEPTION_STR + readableOwnerAddress
          + ActuatorConstant.NOT_EXIST_STR);
    }

    if (!executiveStore.has(ownerAddress)) {
      throw new ContractValidateException(ActuatorConstant.EXECUTIVE_EXCEPTION_STR + readableOwnerAddress
          + ActuatorConstant.NOT_EXIST_STR);
    }

    long latestProposalNum = dynamicStore
        .getLatestProposalNum();
    if (contract.getProposalId() > latestProposalNum) {
      throw new ContractValidateException(ActuatorConstant.PROPOSAL_EXCEPTION_STR + contract.getProposalId()
          + ActuatorConstant.NOT_EXIST_STR);
    }

    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    ProposalCapsule proposalCapsule;
    try {
      proposalCapsule = proposalStore.
          get(ByteArray.fromLong(contract.getProposalId()));
    } catch (ItemNotFoundException ex) {
      throw new ContractValidateException(ActuatorConstant.PROPOSAL_EXCEPTION_STR + contract.getProposalId()
          + ActuatorConstant.NOT_EXIST_STR);
    }

    if (now >= proposalCapsule.getExpirationTime()) {
      throw new ContractValidateException(ActuatorConstant.PROPOSAL_EXCEPTION_STR + contract.getProposalId()
          + "] expired");
    }
    if (proposalCapsule.getState() == State.CANCELED) {
      throw new ContractValidateException(ActuatorConstant.PROPOSAL_EXCEPTION_STR + contract.getProposalId()
          + "] canceled");
    }
    if (!contract.getIsAddApproval()) {
      if (!proposalCapsule.getApprovals().contains(contract.getOwnerAddress())) {
        throw new ContractValidateException(
            ActuatorConstant.EXECUTIVE_EXCEPTION_STR + readableOwnerAddress + "]has not approved proposal[" + contract
                .getProposalId() + "] before");
      }
    } else {
      if (proposalCapsule.getApprovals().contains(contract.getOwnerAddress())) {
        throw new ContractValidateException(
            ActuatorConstant.EXECUTIVE_EXCEPTION_STR + readableOwnerAddress + "]has approved proposal[" + contract
                .getProposalId() + "] before");
      }
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(ProposalApproveContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
