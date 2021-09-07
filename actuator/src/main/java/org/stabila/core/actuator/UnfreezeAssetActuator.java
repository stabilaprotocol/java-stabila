package org.stabila.core.actuator;

import static org.stabila.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.AssetIssueStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.protos.Protocol.Account.Frozen;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.AssetIssueContractOuterClass.UnfreezeAssetContract;

@Slf4j(topic = "actuator")
public class UnfreezeAssetActuator extends AbstractActuator {

  public UnfreezeAssetActuator() {
    super(ContractType.UnfreezeAssetContract, UnfreezeAssetContract.class);
  }

  @Override
  public boolean execute(Object object) throws ContractExeException {
    TransactionResultCapsule ret = (TransactionResultCapsule) object;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(ActuatorConstant.TX_RESULT_NULL);
    }

    long fee = calcFee();
    AccountStore accountStore = chainBaseManager.getAccountStore();
    AssetIssueStore assetIssueStore = chainBaseManager.getAssetIssueStore();
    DynamicPropertiesStore dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    try {
      final UnfreezeAssetContract unfreezeAssetContract = any.unpack(UnfreezeAssetContract.class);
      byte[] ownerAddress = unfreezeAssetContract.getOwnerAddress().toByteArray();

      AccountCapsule accountCapsule = accountStore.get(ownerAddress);
      long unfreezeAsset = 0L;
      List<Frozen> frozenList = Lists.newArrayList();
      frozenList.addAll(accountCapsule.getFrozenSupplyList());
      Iterator<Frozen> iterator = frozenList.iterator();
      long now = dynamicStore.getLatestBlockHeaderTimestamp();
      while (iterator.hasNext()) {
        Frozen next = iterator.next();
        if (next.getExpireTime() <= now) {
          unfreezeAsset += next.getFrozenBalance();
          iterator.remove();
        }
      }

      if (dynamicStore.getAllowSameTokenName() == 0) {
        accountCapsule
            .addAssetAmountV2(accountCapsule.getAssetIssuedName().toByteArray(), unfreezeAsset,
                dynamicStore, assetIssueStore);
      } else {
        accountCapsule
            .addAssetAmountV2(accountCapsule.getAssetIssuedID().toByteArray(), unfreezeAsset,
                dynamicStore, assetIssueStore);
      }

      accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
          .clearFrozenSupply().addAllFrozenSupply(frozenList).build());

      accountStore.put(ownerAddress, accountCapsule);
      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException | ArithmeticException e) {
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
    if (!this.any.is(UnfreezeAssetContract.class)) {
      throw new ContractValidateException(
          "contract type error, expected type [UnfreezeAssetContract], real type[" + any
              .getClass() + "]");
    }
    final UnfreezeAssetContract unfreezeAssetContract;
    try {
      unfreezeAssetContract = this.any.unpack(UnfreezeAssetContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = unfreezeAssetContract.getOwnerAddress().toByteArray();
    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] does not exist");
    }

    if (accountCapsule.getFrozenSupplyCount() <= 0) {
      throw new ContractValidateException("no frozen supply balance");
    }

    if (dynamicStore.getAllowSameTokenName() == 0) {
      if (accountCapsule.getAssetIssuedName().isEmpty()) {
        throw new ContractValidateException("this account has not issued any asset");
      }
    } else {
      if (accountCapsule.getAssetIssuedID().isEmpty()) {
        throw new ContractValidateException("this account has not issued any asset");
      }
    }

    long now = dynamicStore.getLatestBlockHeaderTimestamp();
    long allowedUnfreezeCount = accountCapsule.getFrozenSupplyList().stream()
        .filter(frozen -> frozen.getExpireTime() <= now).count();
    if (allowedUnfreezeCount <= 0) {
      throw new ContractValidateException("It's not time to unfreeze asset supply");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(UnfreezeAssetContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
