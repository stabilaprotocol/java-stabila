package org.stabila.core.actuator;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.stabila.core.vm.utils.MUtil.transfer;
import static org.stabila.core.vm.utils.MUtil.transferToken;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.stabila.core.utils.TransactionUtil;
import org.stabila.core.vm.repository.Repository;
import org.stabila.core.vm.repository.RepositoryImpl;
import org.stabila.common.logsfilter.trigger.ContractTrigger;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.runtime.InternalTransaction;
import org.stabila.common.runtime.InternalTransaction.ExecutorType;
import org.stabila.common.runtime.InternalTransaction.StbType;
import org.stabila.common.runtime.ProgramResult;
import org.stabila.common.utils.StorageUtils;
import org.stabila.common.utils.StringUtil;
import org.stabila.common.utils.WalletUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.ContractCapsule;
import org.stabila.core.capsule.ReceiptCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.db.TransactionContext;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.vm.UcrCost;
import org.stabila.core.vm.LogInfoTriggerParser;
import org.stabila.core.vm.VM;
import org.stabila.core.vm.VMConstant;
import org.stabila.core.vm.VMUtils;
import org.stabila.core.vm.config.ConfigLoader;
import org.stabila.core.vm.config.VMConfig;
import org.stabila.core.vm.program.Program;
import org.stabila.core.vm.program.Program.JVMStackOverFlowException;
import org.stabila.core.vm.program.Program.OutOfTimeException;
import org.stabila.core.vm.program.Program.TransferException;
import org.stabila.core.vm.program.ProgramPrecompile;
import org.stabila.core.vm.program.invoke.ProgramInvoke;
import org.stabila.core.vm.program.invoke.ProgramInvokeFactory;
import org.stabila.core.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.contractResult;
import org.stabila.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "VM")
public class VMActuator implements Actuator2 {

  private Transaction stb;
  private BlockCapsule blockCap;
  private Repository repository;
  private InternalTransaction rootInternalTransaction;
  private ProgramInvokeFactory programInvokeFactory;
  private ReceiptCapsule receipt;


  private VM vm;
  private Program program;
  private VMConfig vmConfig = VMConfig.getInstance();

  @Getter
  @Setter
  private InternalTransaction.StbType stbType;
  private ExecutorType executorType;

  @Getter
  @Setter
  private boolean isConstantCall = false;

  @Setter
  private boolean enableEventListener;

  private LogInfoTriggerParser logInfoTriggerParser;


  public VMActuator(boolean isConstantCall) {
    this.isConstantCall = isConstantCall;
    programInvokeFactory = new ProgramInvokeFactoryImpl();
  }

  private static long getUcrFee(long callerUcrUsage, long callerUcrCded,
      long callerUcrTotal) {
    if (callerUcrTotal <= 0) {
      return 0;
    }
    return BigInteger.valueOf(callerUcrCded).multiply(BigInteger.valueOf(callerUcrUsage))
        .divide(BigInteger.valueOf(callerUcrTotal)).longValueExact();
  }

  @Override
  public void validate(Object object) throws ContractValidateException {

    TransactionContext context = (TransactionContext) object;
    if (Objects.isNull(context)) {
      throw new RuntimeException("TransactionContext is null");
    }

    //Load Config
    ConfigLoader.load(context.getStoreFactory());
    stb = context.getStbCap().getInstance();
    blockCap = context.getBlockCap();
    if (VMConfig.allowSvmCd() && context.getStbCap().getStbTrace() != null) {
      receipt = context.getStbCap().getStbTrace().getReceipt();
    }
    //Route Type
    ContractType contractType = this.stb.getRawData().getContract(0).getType();
    //Prepare Repository
    repository = RepositoryImpl.createRoot(context.getStoreFactory());

    enableEventListener = context.isEventPluginLoaded();

    //set executorType type
    if (Objects.nonNull(blockCap)) {
      this.executorType = ExecutorType.ET_NORMAL_TYPE;
    } else {
      this.blockCap = new BlockCapsule(Block.newBuilder().build());
      this.executorType = ExecutorType.ET_PRE_TYPE;
    }
    if (isConstantCall) {
      this.executorType = ExecutorType.ET_PRE_TYPE;
    }

    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        stbType = StbType.STB_CONTRACT_CALL_TYPE;
        call();
        break;
      case ContractType.CreateSmartContract_VALUE:
        stbType = StbType.STB_CONTRACT_CREATION_TYPE;
        create();
        break;
      default:
        throw new ContractValidateException("Unknown contract type");
    }
  }

  @Override
  public void execute(Object object) throws ContractExeException {
    TransactionContext context = (TransactionContext) object;
    if (Objects.isNull(context)) {
      throw new RuntimeException("TransactionContext is null");
    }

    ProgramResult result = context.getProgramResult();
    try {
      if (vm != null) {
        if (null != blockCap && blockCap.generatedByMyself && blockCap.hasExecutiveSignature()
            && null != TransactionUtil.getContractRet(stb)
            && contractResult.OUT_OF_TIME == TransactionUtil.getContractRet(stb)) {
          result = program.getResult();
          program.spendAllUcr();

          OutOfTimeException e = Program.Exception.alreadyTimeOut();
          result.setRuntimeError(e.getMessage());
          result.setException(e);
          throw e;
        }

        vm.play(program);
        result = program.getResult();

        if (isConstantCall) {
          long callValue = TransactionCapsule.getCallValue(stb.getRawData().getContract(0));
          long callTokenValue = TransactionUtil
              .getCallTokenValue(stb.getRawData().getContract(0));
          if (callValue > 0 || callTokenValue > 0) {
            result.setRuntimeError("constant cannot set call value or call token value.");
            result.rejectInternalTransactions();
          }
          if (result.getException() != null) {
            result.setRuntimeError(result.getException().getMessage());
            result.rejectInternalTransactions();
          }
          context.setProgramResult(result);
          return;
        }

        if (StbType.STB_CONTRACT_CREATION_TYPE == stbType && !result.isRevert()) {
          byte[] code = program.getResult().getHReturn();
          long saveCodeUcr = (long) getLength(code) * UcrCost.getInstance().getCREATE_DATA();
          long afterSpend = program.getUcrLimitLeft().longValue() - saveCodeUcr;
          if (afterSpend < 0) {
            if (null == result.getException()) {
              result.setException(Program.Exception
                  .notEnoughSpendUcr("save just created contract code",
                      saveCodeUcr, program.getUcrLimitLeft().longValue()));
            }
          } else {
            result.spendUcr(saveCodeUcr);
            if (VMConfig.allowSvmConstantinople()) {
              repository.saveCode(program.getContractAddress().getNoLeadZeroesData(), code);
            }
          }
        }

        if (result.getException() != null || result.isRevert()) {
          result.getDeleteAccounts().clear();
          result.getLogInfoList().clear();
          result.resetFutureRefund();
          result.rejectInternalTransactions();

          if (result.getException() != null) {
            if (!(result.getException() instanceof TransferException)) {
              program.spendAllUcr();
            }
            result.setRuntimeError(result.getException().getMessage());
            throw result.getException();
          } else {
            result.setRuntimeError("REVERT opcode executed");
          }
        } else {
          repository.commit();

          if (logInfoTriggerParser != null) {
            List<ContractTrigger> triggers = logInfoTriggerParser
                .parseLogInfos(program.getResult().getLogInfoList(), repository);
            program.getResult().setTriggerList(triggers);
          }

        }
      } else {
        repository.commit();
      }
    } catch (JVMStackOverFlowException e) {
      program.spendAllUcr();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      result.setRuntimeError(result.getException().getMessage());
      logger.info("JVMStackOverFlowException: {}", result.getException().getMessage());
    } catch (OutOfTimeException e) {
      program.spendAllUcr();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      result.setRuntimeError(result.getException().getMessage());
      logger.info("timeout: {}", result.getException().getMessage());
    } catch (Throwable e) {
      if (!(e instanceof TransferException)) {
        program.spendAllUcr();
      }
      result = program.getResult();
      result.rejectInternalTransactions();
      if (Objects.isNull(result.getException())) {
        logger.error(e.getMessage(), e);
        result.setException(new RuntimeException("Unknown Throwable"));
      }
      if (StringUtils.isEmpty(result.getRuntimeError())) {
        result.setRuntimeError(result.getException().getMessage());
      }
      logger.info("runtime result is :{}", result.getException().getMessage());
    }
    //use program returned fill context
    context.setProgramResult(result);

    if (VMConfig.vmTrace() && program != null) {
      String traceContent = program.getTrace()
          .result(result.getHReturn())
          .error(result.getException())
          .toString();

      if (VMConfig.vmTraceCompressed()) {
        traceContent = VMUtils.zipAndEncode(traceContent);
      }

      String txHash = Hex.toHexString(rootInternalTransaction.getHash());
      VMUtils.saveProgramTraceFile(txHash, traceContent);
    }

  }

  private void create()
      throws ContractValidateException {
    if (!repository.getDynamicPropertiesStore().supportVM()) {
      throw new ContractValidateException("vm work is off, need to be opened by the committee");
    }

    CreateSmartContract contract = ContractCapsule.getSmartContractFromTransaction(stb);
    if (contract == null) {
      throw new ContractValidateException("Cannot get CreateSmartContract from transaction");
    }
    SmartContract newSmartContract = contract.getNewContract();
    if (!contract.getOwnerAddress().equals(newSmartContract.getOriginAddress())) {
      logger.info("OwnerAddress not equals OriginAddress");
      throw new ContractValidateException("OwnerAddress is not equals OriginAddress");
    }

    byte[] contractName = newSmartContract.getName().getBytes();

    if (contractName.length > VMConstant.CONTRACT_NAME_LENGTH) {
      throw new ContractValidateException("contractName's length cannot be greater than 32");
    }

    long percent = contract.getNewContract().getConsumeUserResourcePercent();
    if (percent < 0 || percent > VMConstant.ONE_HUNDRED) {
      throw new ContractValidateException("percent must be >= 0 and <= 100");
    }

    byte[] contractAddress = WalletUtil.generateContractAddress(stb);
    // insure the new contract address haven't exist
    if (repository.getAccount(contractAddress) != null) {
      throw new ContractValidateException(
          "Trying to create a contract with existing contract address: " + StringUtil
              .encode58Check(contractAddress));
    }

    newSmartContract = newSmartContract.toBuilder()
        .setContractAddress(ByteString.copyFrom(contractAddress)).build();
    long callValue = newSmartContract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowSvmTransferSrc10()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }
    byte[] callerAddress = contract.getOwnerAddress().toByteArray();
    // create vm to constructor smart contract
    try {
      long feeLimit = stb.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > repository.getDynamicPropertiesStore().getMaxFeeLimit()) {
        logger.info("invalid feeLimit {}", feeLimit);
        throw new ContractValidateException(
            "feeLimit must be >= 0 and <= " + repository.getDynamicPropertiesStore().getMaxFeeLimit());
      }
      AccountCapsule creator = this.repository
          .getAccount(newSmartContract.getOriginAddress().toByteArray());

      long ucrLimit;
      // according to version

      if (StorageUtils.getUcrLimitHardFork()) {
        if (callValue < 0) {
          throw new ContractValidateException("callValue must be >= 0");
        }
        if (tokenValue < 0) {
          throw new ContractValidateException("tokenValue must be >= 0");
        }
        if (newSmartContract.getOriginUcrLimit() <= 0) {
          throw new ContractValidateException("The originUcrLimit must be > 0");
        }
        ucrLimit = getAccountUcrLimitWithFixRatio(creator, feeLimit, callValue);
      } else {
        ucrLimit = getAccountUcrLimitWithFloatRatio(creator, feeLimit, callValue);
      }

      checkTokenValueAndId(tokenValue, tokenId);

      byte[] ops = newSmartContract.getBytecode().toByteArray();
      rootInternalTransaction = new InternalTransaction(stb, stbType);

      long maxCpuTimeOfOneTx = repository.getDynamicPropertiesStore()
          .getMaxCpuTimeOfOneTx() * VMConstant.ONE_THOUSAND;
      long thisTxCPULimitInUs = (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / VMConstant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;
      ProgramInvoke programInvoke = programInvokeFactory
          .createProgramInvoke(StbType.STB_CONTRACT_CREATION_TYPE, executorType, stb,
              tokenValue, tokenId, blockCap.getInstance(), repository, vmStartInUs,
              vmShouldEndInUs, ucrLimit);
      this.vm = new VM();
      this.program = new Program(ops, programInvoke, rootInternalTransaction, vmConfig);
      byte[] txId = TransactionUtil.getTransactionId(stb).getBytes();
      this.program.setRootTransactionId(txId);
      if (enableEventListener && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(),
            txId, callerAddress);
      }
    } catch (Exception e) {
      logger.info(e.getMessage());
      throw new ContractValidateException(e.getMessage());
    }
    program.getResult().setContractAddress(contractAddress);

    repository.createAccount(contractAddress, newSmartContract.getName(),
        Protocol.AccountType.Contract);

    repository.createContract(contractAddress, new ContractCapsule(newSmartContract));
    byte[] code = newSmartContract.getBytecode().toByteArray();
    if (!VMConfig.allowSvmConstantinople()) {
      repository.saveCode(contractAddress, ProgramPrecompile.getCode(code));
    }
    // transfer from callerAddress to contractAddress according to callValue
    if (callValue > 0) {
      transfer(this.repository, callerAddress, contractAddress, callValue);
    }
    if (VMConfig.allowSvmTransferSrc10() && tokenValue > 0) {
      transferToken(this.repository, callerAddress, contractAddress, String.valueOf(tokenId),
          tokenValue);
    }

  }

  /**
   * **
   */

  private void call()
      throws ContractValidateException {

    if (!repository.getDynamicPropertiesStore().supportVM()) {
      logger.info("vm work is off, need to be opened by the committee");
      throw new ContractValidateException("VM work is off, need to be opened by the committee");
    }

    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(stb);
    if (contract == null) {
      return;
    }

    if (contract.getContractAddress() == null) {
      throw new ContractValidateException("Cannot get contract address from TriggerContract");
    }

    byte[] contractAddress = contract.getContractAddress().toByteArray();

    ContractCapsule deployedContract = repository.getContract(contractAddress);
    if (null == deployedContract) {
      logger.info("No contract or not a smart contract");
      throw new ContractValidateException("No contract or not a smart contract");
    }

    long callValue = contract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowSvmTransferSrc10()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }

    if (StorageUtils.getUcrLimitHardFork()) {
      if (callValue < 0) {
        throw new ContractValidateException("callValue must be >= 0");
      }
      if (tokenValue < 0) {
        throw new ContractValidateException("tokenValue must be >= 0");
      }
    }

    byte[] callerAddress = contract.getOwnerAddress().toByteArray();
    checkTokenValueAndId(tokenValue, tokenId);

    byte[] code = repository.getCode(contractAddress);
    if (isNotEmpty(code)) {

      long feeLimit = stb.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > repository.getDynamicPropertiesStore().getMaxFeeLimit()) {
        logger.info("invalid feeLimit {}", feeLimit);
        throw new ContractValidateException(
            "feeLimit must be >= 0 and <= " + repository.getDynamicPropertiesStore().getMaxFeeLimit());
      }
      AccountCapsule caller = repository.getAccount(callerAddress);
      long ucrLimit;
      if (isConstantCall) {
        ucrLimit = VMConstant.UCR_LIMIT_IN_CONSTANT_TX;
      } else {
        AccountCapsule creator = repository
            .getAccount(deployedContract.getInstance().getOriginAddress().toByteArray());
        ucrLimit = getTotalUcrLimit(creator, caller, contract, feeLimit, callValue);
      }

      long maxCpuTimeOfOneTx = repository.getDynamicPropertiesStore()
          .getMaxCpuTimeOfOneTx() * VMConstant.ONE_THOUSAND;
      long thisTxCPULimitInUs =
          (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / VMConstant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;
      ProgramInvoke programInvoke = programInvokeFactory
          .createProgramInvoke(StbType.STB_CONTRACT_CALL_TYPE, executorType, stb,
              tokenValue, tokenId, blockCap.getInstance(), repository, vmStartInUs,
              vmShouldEndInUs, ucrLimit);
      if (isConstantCall) {
        programInvoke.setConstantCall();
      }
      this.vm = new VM();
      rootInternalTransaction = new InternalTransaction(stb, stbType);
      this.program = new Program(code, programInvoke, rootInternalTransaction, vmConfig);
      byte[] txId = TransactionUtil.getTransactionId(stb).getBytes();
      this.program.setRootTransactionId(txId);

      if (enableEventListener && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(),
            txId, callerAddress);
      }
    }

    program.getResult().setContractAddress(contractAddress);
    //transfer from callerAddress to targetAddress according to callValue

    if (callValue > 0) {
      transfer(this.repository, callerAddress, contractAddress, callValue);
    }
    if (VMConfig.allowSvmTransferSrc10() && tokenValue > 0) {
      transferToken(this.repository, callerAddress, contractAddress, String.valueOf(tokenId),
          tokenValue);
    }

  }

  public long getAccountUcrLimitWithFixRatio(AccountCapsule account, long feeLimit,
      long callValue) {

    long unitPerUcr = VMConstant.UNIT_PER_UCR;
    if (repository.getDynamicPropertiesStore().getUcrFee() > 0) {
      unitPerUcr = repository.getDynamicPropertiesStore().getUcrFee();
    }

    long leftCdedUcr = repository.getAccountLeftUcrFromCd(account);
    if (VMConfig.allowSvmCd()) {
      receipt.setCallerUcrLeft(leftCdedUcr);
    }

    long ucrFromBalance = max(account.getBalance() - callValue, 0) / unitPerUcr;
    long availableUcr = Math.addExact(leftCdedUcr, ucrFromBalance);

    long ucrFromFeeLimit = feeLimit / unitPerUcr;
    return min(availableUcr, ucrFromFeeLimit);

  }

  private long getAccountUcrLimitWithFloatRatio(AccountCapsule account, long feeLimit,
      long callValue) {

    long unitPerUcr = VMConstant.UNIT_PER_UCR;
    if (repository.getDynamicPropertiesStore().getUcrFee() > 0) {
      unitPerUcr = repository.getDynamicPropertiesStore().getUcrFee();
    }
    // can change the calc way
    long leftUcrFromCd = repository.getAccountLeftUcrFromCd(account);
    callValue = max(callValue, 0);
    long ucrFromBalance = Math
        .floorDiv(max(account.getBalance() - callValue, 0), unitPerUcr);

    long ucrFromFeeLimit;
    long totalBalanceForUcrCd = account.getAllCdedBalanceForUcr();
    if (0 == totalBalanceForUcrCd) {
      ucrFromFeeLimit =
          feeLimit / unitPerUcr;
    } else {
      long totalUcrFromCd = repository
          .calculateGlobalUcrLimit(account);
      long leftBalanceForUcrCd = getUcrFee(totalBalanceForUcrCd,
          leftUcrFromCd,
          totalUcrFromCd);

      if (leftBalanceForUcrCd >= feeLimit) {
        ucrFromFeeLimit = BigInteger.valueOf(totalUcrFromCd)
            .multiply(BigInteger.valueOf(feeLimit))
            .divide(BigInteger.valueOf(totalBalanceForUcrCd)).longValueExact();
      } else {
        ucrFromFeeLimit = Math
            .addExact(leftUcrFromCd,
                (feeLimit - leftBalanceForUcrCd) / unitPerUcr);
      }
    }

    return min(Math.addExact(leftUcrFromCd, ucrFromBalance), ucrFromFeeLimit);
  }

  public long getTotalUcrLimit(AccountCapsule creator, AccountCapsule caller,
      TriggerSmartContract contract, long feeLimit, long callValue)
      throws ContractValidateException {
    if (Objects.isNull(creator) && VMConfig.allowSvmConstantinople()) {
      return getAccountUcrLimitWithFixRatio(caller, feeLimit, callValue);
    }
    //  according to version
    if (StorageUtils.getUcrLimitHardFork()) {
      return getTotalUcrLimitWithFixRatio(creator, caller, contract, feeLimit, callValue);
    } else {
      return getTotalUcrLimitWithFloatRatio(creator, caller, contract, feeLimit, callValue);
    }
  }


  public void checkTokenValueAndId(long tokenValue, long tokenId) throws ContractValidateException {
    if (VMConfig.allowSvmTransferSrc10() && VMConfig.allowMultiSign()) {
      // tokenid can only be 0
      // or (MIN_TOKEN_ID, Long.Max]
      if (tokenId <= VMConstant.MIN_TOKEN_ID && tokenId != 0) {
        throw new ContractValidateException("tokenId must be > " + VMConstant.MIN_TOKEN_ID);
      }
      // tokenid can only be 0 when tokenvalue = 0,
      // or (MIN_TOKEN_ID, Long.Max]
      if (tokenValue > 0 && tokenId == 0) {
        throw new ContractValidateException("invalid arguments with tokenValue = " + tokenValue +
            ", tokenId = " + tokenId);
      }
    }
  }


  private double getCpuLimitInUsRatio() {

    double cpuLimitRatio;

    if (ExecutorType.ET_NORMAL_TYPE == executorType) {
      // self executive generates block
      if (this.blockCap != null && blockCap.generatedByMyself &&
          !this.blockCap.hasExecutiveSignature()) {
        cpuLimitRatio = 1.0;
      } else {
        // self executive or other executive or fullnode verifies block
        if (stb.getRet(0).getContractRet() == contractResult.OUT_OF_TIME) {
          cpuLimitRatio = CommonParameter.getInstance().getMinTimeRatio();
        } else {
          cpuLimitRatio = CommonParameter.getInstance().getMaxTimeRatio();
        }
      }
    } else {
      // self executive or other executive or fullnode receives tx
      cpuLimitRatio = 1.0;
    }

    return cpuLimitRatio;
  }

  public long getTotalUcrLimitWithFixRatio(AccountCapsule creator, AccountCapsule caller,
      TriggerSmartContract contract, long feeLimit, long callValue)
      throws ContractValidateException {

    long callerUcrLimit = getAccountUcrLimitWithFixRatio(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      // when the creator calls his own contract, this logic will be used.
      // so, the creator must use a BIG feeLimit to call his own contract,
      // which will cost the feeLimit STB when the creator's cded ucr is 0.
      return callerUcrLimit;
    }

    long creatorUcrLimit = 0;
    ContractCapsule contractCapsule = repository
        .getContract(contract.getContractAddress().toByteArray());
    long consumeUserResourcePercent = contractCapsule.getConsumeUserResourcePercent();

    long originUcrLimit = contractCapsule.getOriginUcrLimit();
    if (originUcrLimit < 0) {
      throw new ContractValidateException("originUcrLimit can't be < 0");
    }

    long originUcrLeft = 0;
    if (consumeUserResourcePercent < VMConstant.ONE_HUNDRED) {
      originUcrLeft = repository.getAccountLeftUcrFromCd(creator);
      if (VMConfig.allowSvmCd()) {
        receipt.setOriginUcrLeft(originUcrLeft);
      }
    }
    if (consumeUserResourcePercent <= 0) {
      creatorUcrLimit = min(originUcrLeft, originUcrLimit);
    } else {
      if (consumeUserResourcePercent < VMConstant.ONE_HUNDRED) {
        // creatorUcrLimit =
        // min(callerUcrLimit * (100 - percent) / percent, creatorLeftCdedUcr, originUcrLimit)

        creatorUcrLimit = min(
            BigInteger.valueOf(callerUcrLimit)
                .multiply(BigInteger.valueOf(VMConstant.ONE_HUNDRED - consumeUserResourcePercent))
                .divide(BigInteger.valueOf(consumeUserResourcePercent)).longValueExact(),
            min(originUcrLeft, originUcrLimit)
        );
      }
    }
    return Math.addExact(callerUcrLimit, creatorUcrLimit);
  }

  private long getTotalUcrLimitWithFloatRatio(AccountCapsule creator, AccountCapsule caller,
      TriggerSmartContract contract, long feeLimit, long callValue) {

    long callerUcrLimit = getAccountUcrLimitWithFloatRatio(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      return callerUcrLimit;
    }

    // creatorUcrFromCd
    long creatorUcrLimit = repository.getAccountLeftUcrFromCd(creator);

    ContractCapsule contractCapsule = repository
        .getContract(contract.getContractAddress().toByteArray());
    long consumeUserResourcePercent = contractCapsule.getConsumeUserResourcePercent();

    if (creatorUcrLimit * consumeUserResourcePercent
        > (VMConstant.ONE_HUNDRED - consumeUserResourcePercent) * callerUcrLimit) {
      return Math.floorDiv(callerUcrLimit * VMConstant.ONE_HUNDRED, consumeUserResourcePercent);
    } else {
      return Math.addExact(callerUcrLimit, creatorUcrLimit);
    }
  }

  private boolean isCheckTransaction() {
    return this.blockCap != null && !this.blockCap.getInstance().getBlockHeader()
        .getExecutiveSignature().isEmpty();
  }


}
