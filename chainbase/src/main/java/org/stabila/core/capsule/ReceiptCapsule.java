package org.stabila.core.capsule;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.stabila.core.db.UcrProcessor;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.Commons;
import org.stabila.common.utils.ForkController;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.Constant;
import org.stabila.core.config.Parameter.ForkBlockVersionEnum;
import org.stabila.core.exception.BalanceInsufficientException;
import org.stabila.protos.Protocol.ResourceReceipt;
import org.stabila.protos.Protocol.Transaction.Result.contractResult;

public class ReceiptCapsule {

  private ResourceReceipt receipt;
  @Getter
  @Setter
  private long multiSignFee;

  /**
   * Available ucr of contract deployer before executing transaction
   */
  @Setter
  private long originUcrLeft;

  /**
   * Available ucr of caller before executing transaction
   */
  @Setter
  private long callerUcrLeft;

  private Sha256Hash receiptAddress;

  public ReceiptCapsule(ResourceReceipt data, Sha256Hash receiptAddress) {
    this.receipt = data;
    this.receiptAddress = receiptAddress;
  }

  public ReceiptCapsule(Sha256Hash receiptAddress) {
    this.receipt = ResourceReceipt.newBuilder().build();
    this.receiptAddress = receiptAddress;
  }

  public static ResourceReceipt copyReceipt(ReceiptCapsule origin) {
    return origin.getReceipt().toBuilder().build();
  }

  public static boolean checkForUcrLimit(DynamicPropertiesStore ds) {
    long blockNum = ds.getLatestBlockHeaderNumber();
    return blockNum >= CommonParameter.getInstance()
        .getBlockNumForUcrLimit();
  }

  public ResourceReceipt getReceipt() {
    return this.receipt;
  }

  public void setReceipt(ResourceReceipt receipt) {
    this.receipt = receipt;
  }

  public Sha256Hash getReceiptAddress() {
    return this.receiptAddress;
  }

  public void addNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(getNetFee() + netFee).build();
  }

  public long getUcrUsage() {
    return this.receipt.getUcrUsage();
  }

  public void setUcrUsage(long ucrUsage) {
    this.receipt = this.receipt.toBuilder().setUcrUsage(ucrUsage).build();
  }

  public long getUcrFee() {
    return this.receipt.getUcrFee();
  }

  public void setUcrFee(long ucrFee) {
    this.receipt = this.receipt.toBuilder().setUcrFee(ucrFee).build();
  }

  public long getOriginUcrUsage() {
    return this.receipt.getOriginUcrUsage();
  }

  public void setOriginUcrUsage(long ucrUsage) {
    this.receipt = this.receipt.toBuilder().setOriginUcrUsage(ucrUsage).build();
  }

  public long getUcrUsageTotal() {
    return this.receipt.getUcrUsageTotal();
  }

  public void setUcrUsageTotal(long ucrUsage) {
    this.receipt = this.receipt.toBuilder().setUcrUsageTotal(ucrUsage).build();
  }

  public long getNetUsage() {
    return this.receipt.getNetUsage();
  }

  public void setNetUsage(long netUsage) {
    this.receipt = this.receipt.toBuilder().setNetUsage(netUsage).build();
  }

  public long getNetFee() {
    return this.receipt.getNetFee();
  }

  public void setNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(netFee).build();
  }

  /**
   * payUcrBill pay receipt ucr bill by ucr processor.
   */
  public void payUcrBill(DynamicPropertiesStore dynamicPropertiesStore,
                            AccountStore accountStore, ForkController forkController, AccountCapsule origin,
                            AccountCapsule caller,
                            long percent, long originUcrLimit, UcrProcessor ucrProcessor, long now)
      throws BalanceInsufficientException {
    if (receipt.getUcrUsageTotal() <= 0) {
      return;
    }

    if (Objects.isNull(origin) && dynamicPropertiesStore.getAllowSvmConstantinople() == 1) {
      payUcrBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getUcrUsageTotal(), receipt.getResult(), ucrProcessor, now);
      return;
    }

    if ((!Objects.isNull(origin))&&caller.getAddress().equals(origin.getAddress())) {
      payUcrBill(dynamicPropertiesStore, accountStore, forkController, caller,
          receipt.getUcrUsageTotal(), receipt.getResult(), ucrProcessor, now);
    } else {
      long originUsage = Math.multiplyExact(receipt.getUcrUsageTotal(), percent) / 100;
      originUsage = getOriginUsage(dynamicPropertiesStore, origin, originUcrLimit,
              ucrProcessor,
          originUsage);

      long callerUsage = receipt.getUcrUsageTotal() - originUsage;
      ucrProcessor.useUcr(origin, originUsage, now);
      this.setOriginUcrUsage(originUsage);
      payUcrBill(dynamicPropertiesStore, accountStore, forkController,
          caller, callerUsage, receipt.getResult(), ucrProcessor, now);
    }
  }

  private long getOriginUsage(DynamicPropertiesStore dynamicPropertiesStore, AccountCapsule origin,
                              long originUcrLimit,
                              UcrProcessor ucrProcessor, long originUsage) {

    if (dynamicPropertiesStore.getAllowSvmCd() == 1) {
      return Math.min(originUsage, Math.min(originUcrLeft, originUcrLimit));
    }

    if (checkForUcrLimit(dynamicPropertiesStore)) {
      return Math.min(originUsage,
          Math.min(ucrProcessor.getAccountLeftUcrFromCd(origin), originUcrLimit));
    }
    return Math.min(originUsage, ucrProcessor.getAccountLeftUcrFromCd(origin));
  }

  private void payUcrBill(
      DynamicPropertiesStore dynamicPropertiesStore, AccountStore accountStore,
      ForkController forkController,
      AccountCapsule account,
      long usage,
      contractResult contractResult,
      UcrProcessor ucrProcessor,
      long now) throws BalanceInsufficientException {
    long accountUcrLeft;
    if (dynamicPropertiesStore.getAllowSvmCd() == 1) {
      accountUcrLeft = callerUcrLeft;
    } else {
      accountUcrLeft = ucrProcessor.getAccountLeftUcrFromCd(account);
    }
    if (accountUcrLeft >= usage) {
      ucrProcessor.useUcr(account, usage, now);
      this.setUcrUsage(usage);
    } else {
      ucrProcessor.useUcr(account, accountUcrLeft, now);

      if (forkController.pass(ForkBlockVersionEnum.VERSION_3_6_5) &&
          dynamicPropertiesStore.getAllowAdaptiveUcr() == 1) {
        long blockUcrUsage =
            dynamicPropertiesStore.getBlockUcrUsage() + (usage - accountUcrLeft);
        dynamicPropertiesStore.saveBlockUcrUsage(blockUcrUsage);
      }

      long unitPerUcr = Constant.UNIT_PER_UCR;
      long dynamicUcrFee = dynamicPropertiesStore.getUcrFee();
      if (dynamicUcrFee > 0) {
        unitPerUcr = dynamicUcrFee;
      }
      long ucrFee =
          (usage - accountUcrLeft) * unitPerUcr;
      this.setUcrUsage(accountUcrLeft);
      this.setUcrFee(ucrFee);
      long balance = account.getBalance();
      if (balance < ucrFee) {
        throw new BalanceInsufficientException(
            StringUtil.createReadableString(account.createDbKey()) + " insufficient balance");
      }
      account.setBalance(balance - ucrFee);

      if (dynamicPropertiesStore.supportTransactionFeePool() &&
          !contractResult.equals(contractResult.OUT_OF_TIME)) {
        dynamicPropertiesStore.addTransactionFeePool(ucrFee);
      } else if (dynamicPropertiesStore.supportBlackHoleOptimization()) {
        dynamicPropertiesStore.burnStb(ucrFee);
      } else {
        //send to blackHole
        Commons.adjustBalance(accountStore, accountStore.getUnit(),
            ucrFee);
      }

    }

    accountStore.put(account.getAddress().toByteArray(), account);
  }

  public contractResult getResult() {
    return this.receipt.getResult();
  }

  public void setResult(contractResult success) {
    this.receipt = receipt.toBuilder().setResult(success).build();
  }
}
