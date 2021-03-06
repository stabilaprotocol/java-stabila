package org.stabila.core.db;

import static org.stabila.common.runtime.InternalTransaction.StbType.STB_CONTRACT_CALL_TYPE;
import static org.stabila.common.runtime.InternalTransaction.StbType.STB_CONTRACT_CREATION_TYPE;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.util.StringUtils;
import org.stabila.common.runtime.InternalTransaction.StbType;
import org.stabila.common.runtime.ProgramResult;
import org.stabila.common.runtime.Runtime;
import org.stabila.common.runtime.vm.DataWord;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.common.utils.ForkController;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.common.utils.StringUtil;
import org.stabila.common.utils.WalletUtil;
import org.stabila.core.Constant;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.ContractCapsule;
import org.stabila.core.capsule.ReceiptCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.exception.BalanceInsufficientException;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.ReceiptCheckErrException;
import org.stabila.core.exception.VMIllegalException;
import org.stabila.core.store.AbiStore;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.CodeStore;
import org.stabila.core.store.ContractStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.StoreFactory;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.contractResult;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract.ABI;
import org.stabila.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "TransactionTrace")
public class TransactionTrace {

  private TransactionCapsule stb;

  private ReceiptCapsule receipt;

  private StoreFactory storeFactory;

  private DynamicPropertiesStore dynamicPropertiesStore;

  private ContractStore contractStore;

  private AccountStore accountStore;

  private CodeStore codeStore;

  private AbiStore abiStore;

  private UcrProcessor ucrProcessor;

  private StbType stbType;

  private long txStartTimeInMs;

  private Runtime runtime;

  private ForkController forkController;

  @Getter
  private TransactionContext transactionContext;
  @Getter
  @Setter
  private TimeResultType timeResultType = TimeResultType.NORMAL;
  @Getter
  @Setter
  private boolean netFeeForBandwidth = true;

  public TransactionTrace(TransactionCapsule stb, StoreFactory storeFactory,
      Runtime runtime) {
    this.stb = stb;
    Transaction.Contract.ContractType contractType = this.stb.getInstance().getRawData()
        .getContract(0).getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        stbType = STB_CONTRACT_CALL_TYPE;
        break;
      case ContractType.CreateSmartContract_VALUE:
        stbType = STB_CONTRACT_CREATION_TYPE;
        break;
      default:
        stbType = StbType.STB_PRECOMPILED_TYPE;
    }
    this.storeFactory = storeFactory;
    this.dynamicPropertiesStore = storeFactory.getChainBaseManager().getDynamicPropertiesStore();
    this.contractStore = storeFactory.getChainBaseManager().getContractStore();
    this.codeStore = storeFactory.getChainBaseManager().getCodeStore();
    this.abiStore = storeFactory.getChainBaseManager().getAbiStore();
    this.accountStore = storeFactory.getChainBaseManager().getAccountStore();

    this.receipt = new ReceiptCapsule(Sha256Hash.ZERO_HASH);
    this.ucrProcessor = new UcrProcessor(dynamicPropertiesStore, accountStore);
    this.runtime = runtime;
    this.forkController = new ForkController();
    forkController.init(storeFactory.getChainBaseManager());
  }

  public TransactionCapsule getStb() {
    return stb;
  }

  private boolean needVM() {
    return this.stbType == STB_CONTRACT_CALL_TYPE
        || this.stbType == STB_CONTRACT_CREATION_TYPE;
  }

  public void init(BlockCapsule blockCap) {
    init(blockCap, false);
  }

  //pre transaction check
  public void init(BlockCapsule blockCap, boolean eventPluginLoaded) {
    txStartTimeInMs = System.currentTimeMillis();
    transactionContext = new TransactionContext(blockCap, stb, storeFactory, false,
        eventPluginLoaded);
  }

  public void checkIsConstant() throws ContractValidateException, VMIllegalException {
    if (dynamicPropertiesStore.getAllowSvmConstantinople() == 1) {
      return;
    }
    TriggerSmartContract triggerContractFromTransaction = ContractCapsule
        .getTriggerContractFromTransaction(this.getStb().getInstance());
    if (STB_CONTRACT_CALL_TYPE == this.stbType) {
      ContractCapsule contract = contractStore
          .get(triggerContractFromTransaction.getContractAddress().toByteArray());
      if (contract == null) {
        logger.info("contract: {} is not in contract store", StringUtil
            .encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray()));
        throw new ContractValidateException("contract: " + StringUtil
            .encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray())
            + " is not in contract store");
      }
      ABI abi = contract.getInstance().getAbi();
      if (WalletUtil.isConstant(abi, triggerContractFromTransaction)) {
        throw new VMIllegalException("cannot call constant method");
      }
    }
  }

  //set bill
  public void setBill(long ucrUsage) {
    if (ucrUsage < 0) {
      ucrUsage = 0L;
    }
    receipt.setUcrUsageTotal(ucrUsage);
  }

  //set net bill
  public void setNetBill(long netUsage, long netFee) {
    receipt.setNetUsage(netUsage);
    receipt.setNetFee(netFee);
  }

  public void setNetBillForCreateNewAccount(long netUsage, long netFee) {
    receipt.setNetUsage(netUsage);
    receipt.setNetFee(netFee);
    setNetFeeForBandwidth(false);
  }

  public void addNetBill(long netFee) {
    receipt.addNetFee(netFee);
  }

  public void exec()
      throws ContractExeException, ContractValidateException, VMIllegalException {
    /*  VM execute  */
    runtime.execute(transactionContext);
    setBill(transactionContext.getProgramResult().getUcrUsed());

//    if (StbType.STB_PRECOMPILED_TYPE != stbType) {
//      if (contractResult.OUT_OF_TIME
//          .equals(receipt.getResult())) {
//        setTimeResultType(TimeResultType.OUT_OF_TIME);
//      } else if (System.currentTimeMillis() - txStartTimeInMs
//          > CommonParameter.getInstance()
//          .getLongRunningTime()) {
//        setTimeResultType(TimeResultType.LONG_RUNNING);
//      }
//    }
  }

  public void saveUcrLeftOfOrigin(long ucrLeft) {
    receipt.setOriginUcrLeft(ucrLeft);
  }

  public void saveUcrLeftOfCaller(long ucrLeft) {
    receipt.setCallerUcrLeft(ucrLeft);
  }

  public void finalization() throws ContractExeException {
    try {
      pay();
    } catch (BalanceInsufficientException e) {
      throw new ContractExeException(e.getMessage());
    }
    if (StringUtils.isEmpty(transactionContext.getProgramResult().getRuntimeError())) {
      for (DataWord contract : transactionContext.getProgramResult().getDeleteAccounts()) {
        deleteContract(convertToStabilaAddress((contract.getLast20Bytes())));
      }
    }
  }

  /**
   * pay actually bill(include UCR and storage).
   */
  public void pay() throws BalanceInsufficientException {
    byte[] originAccount;
    byte[] callerAccount;
    long percent = 0;
    long originUcrLimit = 0;
    switch (stbType) {
      case STB_CONTRACT_CREATION_TYPE:
        callerAccount = TransactionCapsule.getOwner(stb.getInstance().getRawData().getContract(0));
        originAccount = callerAccount;
        break;
      case STB_CONTRACT_CALL_TYPE:
        TriggerSmartContract callContract = ContractCapsule
            .getTriggerContractFromTransaction(stb.getInstance());
        ContractCapsule contractCapsule =
            contractStore.get(callContract.getContractAddress().toByteArray());

        callerAccount = callContract.getOwnerAddress().toByteArray();
        originAccount = contractCapsule.getOriginAddress();
        percent = Math
            .max(Constant.ONE_HUNDRED - contractCapsule.getConsumeUserResourcePercent(), 0);
        percent = Math.min(percent, Constant.ONE_HUNDRED);
        originUcrLimit = contractCapsule.getOriginUcrLimit();
        break;
      default:
        return;
    }

    // originAccount Percent = 30%
    AccountCapsule origin = accountStore.get(originAccount);
    AccountCapsule caller = accountStore.get(callerAccount);
    receipt.payUcrBill(
        dynamicPropertiesStore, accountStore, forkController,
        origin,
        caller,
        percent, originUcrLimit,
            ucrProcessor,
        UcrProcessor.getHeadSlot(dynamicPropertiesStore));
  }

  public boolean checkNeedRetry() {
    if (!needVM()) {
      return false;
    }
    return stb.getContractRet() != contractResult.OUT_OF_TIME && receipt.getResult()
        == contractResult.OUT_OF_TIME;
  }

  public void check() throws ReceiptCheckErrException {
    if (!needVM()) {
      return;
    }
    if (Objects.isNull(stb.getContractRet())) {
      throw new ReceiptCheckErrException("null resultCode");
    }
    if (!stb.getContractRet().equals(receipt.getResult())) {
      logger.info(
          "this tx id: {}, the resultCode in received block: {}, the resultCode in self: {}",
          Hex.toHexString(stb.getTransactionId().getBytes()), stb.getContractRet(),
          receipt.getResult());
      throw new ReceiptCheckErrException("Different resultCode");
    }
  }

  public ReceiptCapsule getReceipt() {
    return receipt;
  }

  public void setResult() {
    if (!needVM()) {
      return;
    }
    receipt.setResult(transactionContext.getProgramResult().getResultCode());
  }

  public String getRuntimeError() {
    return transactionContext.getProgramResult().getRuntimeError();
  }

  public ProgramResult getRuntimeResult() {
    return transactionContext.getProgramResult();
  }

  public Runtime getRuntime() {
    return runtime;
  }

  public void deleteContract(byte[] address) {
    abiStore.delete(address);
    codeStore.delete(address);
    accountStore.delete(address);
    contractStore.delete(address);
  }

  public static byte[] convertToStabilaAddress(byte[] address) {
    if (address.length == 20) {
      byte[] newAddress = new byte[21];
      byte[] temp = new byte[]{DecodeUtil.addressPreFixByte};
      System.arraycopy(temp, 0, newAddress, 0, temp.length);
      System.arraycopy(address, 0, newAddress, temp.length, address.length);
      address = newAddress;
    }
    return address;
  }

  public enum TimeResultType {
    NORMAL,
    LONG_RUNNING,
    OUT_OF_TIME
  }
}
