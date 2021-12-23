/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

package org.stabila.core.vm.program;

import static java.lang.StrictMath.min;
import static java.lang.String.format;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;
import static org.stabila.common.utils.ByteUtil.stripLeadingZeroes;

import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.stabila.core.config.Parameter;
import org.stabila.core.db.TransactionTrace;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.StabilaException;
import org.stabila.core.utils.TransactionUtil;
import org.stabila.core.vm.nativecontract.CdBalanceProcessor;
import org.stabila.core.vm.nativecontract.UncdBalanceProcessor;
import org.stabila.core.vm.nativecontract.VoteExecutiveProcessor;
import org.stabila.core.vm.nativecontract.WithdrawRewardProcessor;
import org.stabila.core.vm.nativecontract.param.CdBalanceParam;
import org.stabila.core.vm.nativecontract.param.UncdBalanceParam;
import org.stabila.core.vm.nativecontract.param.VoteExecutiveParam;
import org.stabila.core.vm.nativecontract.param.WithdrawRewardParam;
import org.stabila.core.vm.repository.Repository;
import org.stabila.core.vm.trace.ProgramTrace;
import org.stabila.core.vm.trace.ProgramTraceListener;
import org.stabila.core.vm.utils.MUtil;
import org.stabila.core.vm.utils.VoteRewardUtil;
import org.stabila.common.crypto.Hash;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.runtime.InternalTransaction;
import org.stabila.common.runtime.ProgramResult;
import org.stabila.common.runtime.vm.DataWord;
import org.stabila.common.utils.BIUtil;
import org.stabila.common.utils.ByteUtil;
import org.stabila.common.utils.FastByteComparisons;
import org.stabila.common.utils.Utils;
import org.stabila.common.utils.WalletUtil;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.ContractCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.capsule.VotesCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.vm.UcrCost;
import org.stabila.core.vm.MessageCall;
import org.stabila.core.vm.OpCode;
import org.stabila.core.vm.PrecompiledContracts;
import org.stabila.core.vm.VM;
import org.stabila.core.vm.VMConstant;
import org.stabila.core.vm.VMUtils;
import org.stabila.core.vm.config.VMConfig;
import org.stabila.core.vm.program.invoke.ProgramInvoke;
import org.stabila.core.vm.program.invoke.ProgramInvokeFactory;
import org.stabila.core.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.stabila.core.vm.program.listener.CompositeProgramListener;
import org.stabila.core.vm.program.listener.ProgramListenerAware;
import org.stabila.core.vm.program.listener.ProgramStorageChangeListener;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.contract.Common;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract.Builder;

/**
 * @author Roman Mandeleil
 * @since 01.06.2014
 */

@Slf4j(topic = "VM")
public class Program {

  private static final int MAX_DEPTH = 64;
  //Max size for stack checks
  private static final int MAX_STACK_SIZE = 1024;
  private static final String VALIDATE_FOR_SMART_CONTRACT_FAILURE =
      "validateForSmartContract failure:%s";
  private static final String INVALID_TOKEN_ID_MSG = "not valid token id";
  private static final String REFUND_UCR_FROM_MESSAGE_CALL = "refund ucr from message call";
  private static final String CALL_PRE_COMPILED = "call pre-compiled";
  private final VMConfig config;
  private long nonce;
  private byte[] rootTransactionId;
  private InternalTransaction internalTransaction;
  private ProgramInvoke invoke;
  private ProgramInvokeFactory programInvokeFactory = new ProgramInvokeFactoryImpl();
  private ProgramOutListener listener;
  private ProgramTraceListener traceListener;
  private ProgramStorageChangeListener storageDiffListener = new ProgramStorageChangeListener();
  private CompositeProgramListener programListener = new CompositeProgramListener();
  private Stack stack;
  private Memory memory;
  private ContractState contractState;
  private byte[] returnDataBuffer;
  private ProgramResult result = new ProgramResult();
  private ProgramTrace trace = new ProgramTrace();
  private byte[] ops;
  private int pc;
  private byte lastOp;
  private byte previouslyExecutedOp;
  private boolean stopped;
  private ProgramPrecompile programPrecompile;

  public Program(byte[] ops, ProgramInvoke programInvoke) {
    this(ops, programInvoke, null);
  }

  public Program(byte[] ops, ProgramInvoke programInvoke, InternalTransaction internalTransaction) {
    this(ops, programInvoke, internalTransaction, VMConfig.getInstance());
  }

  public Program(byte[] ops, ProgramInvoke programInvoke, InternalTransaction internalTransaction,
      VMConfig config) {
    this.config = config;
    this.invoke = programInvoke;
    this.internalTransaction = internalTransaction;
    this.ops = nullToEmpty(ops);

    traceListener = new ProgramTraceListener(config.vmTrace());
    this.memory = setupProgramListener(new Memory());
    this.stack = setupProgramListener(new Stack());
    this.contractState = setupProgramListener(new ContractState(programInvoke));
    this.trace = new ProgramTrace(config, programInvoke);
    this.nonce = internalTransaction.getNonce();
  }

  static String formatBinData(byte[] binData, int startPC) {
    StringBuilder ret = new StringBuilder();
    for (int i = 0; i < binData.length; i += 16) {
      ret.append(Utils.align("" + Integer.toHexString(startPC + (i)) + ":", ' ', 8, false));
      ret.append(Hex.toHexString(binData, i, min(16, binData.length - i))).append('\n');
    }
    return ret.toString();
  }

  public static String stringifyMultiline(byte[] code) {
    int index = 0;
    StringBuilder sb = new StringBuilder();
    BitSet mask = buildReachableBytecodesMask(code);
    ByteArrayOutputStream binData = new ByteArrayOutputStream();
    int binDataStartPC = -1;

    while (index < code.length) {
      final byte opCode = code[index];
      OpCode op = OpCode.code(opCode);

      if (!mask.get(index)) {
        if (binDataStartPC == -1) {
          binDataStartPC = index;
        }
        binData.write(code[index]);
        index++;
        if (index < code.length) {
          continue;
        }
      }

      if (binDataStartPC != -1) {
        sb.append(formatBinData(binData.toByteArray(), binDataStartPC));
        binDataStartPC = -1;
        binData = new ByteArrayOutputStream();
        if (index == code.length) {
          continue;
        }
      }

      sb.append(Utils.align("" + Integer.toHexString(index) + ":", ' ', 8, false));

      if (op == null) {
        sb.append("<UNKNOWN>: ").append(0xFF & opCode).append("\n");
        index++;
        continue;
      }

      if (op.name().startsWith("PUSH")) {
        sb.append(' ').append(op.name()).append(' ');

        int nPush = op.val() - OpCode.PUSH1.val() + 1;
        byte[] data = Arrays.copyOfRange(code, index + 1, index + nPush + 1);
        BigInteger bi = new BigInteger(1, data);
        sb.append("0x").append(bi.toString(16));
        if (bi.bitLength() <= 32) {
          sb.append(" (").append(new BigInteger(1, data).toString()).append(") ");
        }

        index += nPush + 1;
      } else {
        sb.append(' ').append(op.name());
        index++;
      }
      sb.append('\n');
    }

    return sb.toString();
  }

  static BitSet buildReachableBytecodesMask(byte[] code) {
    NavigableSet<Integer> gotos = new TreeSet<>();
    ByteCodeIterator it = new ByteCodeIterator(code);
    BitSet ret = new BitSet(code.length);
    int lastPush = 0;
    int lastPushPC = 0;
    do {
      ret.set(it.getPC()); // reachable bytecode
      if (it.isPush()) {
        lastPush = new BigInteger(1, it.getCurOpcodeArg()).intValue();
        lastPushPC = it.getPC();
      }
      if (it.getCurOpcode() == OpCode.JUMP || it.getCurOpcode() == OpCode.JUMPI) {
        if (it.getPC() != lastPushPC + 1) {
          // some PC arithmetic we totally can't deal with
          // assuming all bytecodes are reachable as a fallback
          ret.set(0, code.length);
          return ret;
        }
        int jumpPC = lastPush;
        if (!ret.get(jumpPC)) {
          // code was not explored yet
          gotos.add(jumpPC);
        }
      }
      if (it.getCurOpcode() == OpCode.JUMP || it.getCurOpcode() == OpCode.RETURN
          || it.getCurOpcode() == OpCode.STOP) {
        if (gotos.isEmpty()) {
          break;
        }
        it.setPC(gotos.pollFirst());
      }
    } while (it.next());
    return ret;
  }

  public static String stringify(byte[] code) {
    int index = 0;
    StringBuilder sb = new StringBuilder();

    while (index < code.length) {
      final byte opCode = code[index];
      OpCode op = OpCode.code(opCode);

      if (op == null) {
        sb.append(" <UNKNOWN>: ").append(0xFF & opCode).append(" ");
        index++;
        continue;
      }

      if (op.name().startsWith("PUSH")) {
        sb.append(' ').append(op.name()).append(' ');

        int nPush = op.val() - OpCode.PUSH1.val() + 1;
        byte[] data = Arrays.copyOfRange(code, index + 1, index + nPush + 1);
        BigInteger bi = new BigInteger(1, data);
        sb.append("0x").append(bi.toString(16)).append(" ");

        index += nPush + 1;
      } else {
        sb.append(' ').append(op.name());
        index++;
      }
    }

    return sb.toString();
  }

  public byte[] getRootTransactionId() {
    return rootTransactionId.clone();
  }

  public void setRootTransactionId(byte[] rootTransactionId) {
    this.rootTransactionId = rootTransactionId.clone();
  }

  public long getNonce() {
    return nonce;
  }

  public void setNonce(long nonceValue) {
    nonce = nonceValue;
  }

  public ProgramPrecompile getProgramPrecompile() {
    if (programPrecompile == null) {
      programPrecompile = ProgramPrecompile.compile(ops);
    }
    return programPrecompile;
  }

  public int getCallDeep() {
    return invoke.getCallDeep();
  }

  /**
   * @param transferAddress the address send STB to.
   * @param value the STB value transferred in the internal transaction
   */
  private InternalTransaction addInternalTx(DataWord ucrLimit, byte[] senderAddress,
      byte[] transferAddress,
      long value, byte[] data, String note, long nonce, Map<String, Long> tokenInfo) {

    InternalTransaction addedInternalTx = null;
    if (internalTransaction != null) {
      addedInternalTx = getResult()
          .addInternalTransaction(internalTransaction.getHash(), getCallDeep(),
              senderAddress, transferAddress, value, data, note, nonce, tokenInfo);
    }

    return addedInternalTx;
  }

  private <T extends ProgramListenerAware> T setupProgramListener(T programListenerAware) {
    if (programListener.isEmpty()) {
      programListener.addListener(traceListener);
      programListener.addListener(storageDiffListener);
    }

    programListenerAware.setProgramListener(programListener);

    return programListenerAware;
  }

  public Map<DataWord, DataWord> getStorageDiff() {
    return storageDiffListener.getDiff();
  }

  public byte getOp(int pc) {
    return (getLength(ops) <= pc) ? 0 : ops[pc];
  }

  public byte getCurrentOp() {
    return isEmpty(ops) ? 0 : ops[pc];
  }

  /**
   * Last Op can only be set publicly (no getLastOp method), is used for logging.
   */
  public void setLastOp(byte op) {
    this.lastOp = op;
  }

  /**
   * Returns the last fully executed OP.
   */
  public byte getPreviouslyExecutedOp() {
    return this.previouslyExecutedOp;
  }

  /**
   * Should be set only after the OP is fully executed.
   */
  public void setPreviouslyExecutedOp(byte op) {
    this.previouslyExecutedOp = op;
  }

  public void stackPush(byte[] data) {
    stackPush(new DataWord(data));
  }

  public void stackPush(DataWord stackWord) {
    verifyStackOverflow(0, 1); //Sanity Check
    stack.push(stackWord);
  }

  public void stackPushZero() {
    stackPush(new DataWord(0));
  }

  public void stackPushOne() {
    DataWord stackWord = new DataWord(1);
    stackPush(stackWord);
  }

  public Stack getStack() {
    return this.stack;
  }

  public int getPC() {
    return pc;
  }

  public void setPC(DataWord pc) {
    this.setPC(pc.intValue());
  }

  public void setPC(int pc) {
    this.pc = pc;

    if (this.pc >= ops.length) {
      stop();
    }
  }

  public boolean isStopped() {
    return stopped;
  }

  public void stop() {
    stopped = true;
  }

  public void setHReturn(byte[] buff) {
    getResult().setHReturn(buff);
  }

  public void step() {
    setPC(pc + 1);
  }

  public byte[] sweep(int n) {

    if (pc + n > ops.length) {
      stop();
    }

    byte[] data = Arrays.copyOfRange(ops, pc, pc + n);
    pc += n;
    if (pc >= ops.length) {
      stop();
    }

    return data;
  }

  public DataWord stackPop() {
    return stack.pop();
  }

  /**
   * . Verifies that the stack is at least <code>stackSize</code>
   *
   * @param stackSize int
   * @throws StackTooSmallException If the stack is smaller than <code>stackSize</code>
   */
  public void verifyStackSize(int stackSize) {
    if (stack.size() < stackSize) {
      throw Exception.tooSmallStack(stackSize, stack.size());
    }
  }

  public void verifyStackOverflow(int argsReqs, int returnReqs) {
    if ((stack.size() - argsReqs + returnReqs) > MAX_STACK_SIZE) {
      throw new StackTooLargeException(
          "Expected: overflow " + MAX_STACK_SIZE + " elements stack limit");
    }
  }

  public int getMemSize() {
    return memory.size();
  }

  public void memorySave(DataWord addrB, DataWord value) {
    memory.write(addrB.intValue(), value.getData(), value.getData().length, false);
  }

  public void memorySave(int addr, byte[] value) {
    memory.write(addr, value, value.length, false);
  }

  /**
   * . Allocates a piece of memory and stores value at given offset address
   *
   * @param addr is the offset address
   * @param allocSize size of memory needed to write
   * @param value the data to write to memory
   */
  public void memorySave(int addr, int allocSize, byte[] value) {
    memory.extendAndWrite(addr, allocSize, value);
  }

  public void memorySaveLimited(int addr, byte[] data, int dataSize) {
    memory.write(addr, data, dataSize, true);
  }

  public void memoryExpand(DataWord outDataOffs, DataWord outDataSize) {
    if (!outDataSize.isZero()) {
      memory.extend(outDataOffs.intValue(), outDataSize.intValue());
    }
  }

  public DataWord memoryLoad(DataWord addr) {
    return memory.readWord(addr.intValue());
  }

  public DataWord memoryLoad(int address) {
    return memory.readWord(address);
  }

  public byte[] memoryChunk(int offset, int size) {
    return memory.read(offset, size);
  }

  /**
   * . Allocates extra memory in the program for a specified size, calculated from a given offset
   *
   * @param offset the memory address offset
   * @param size the number of bytes to allocate
   */
  public void allocateMemory(int offset, int size) {
    memory.extend(offset, size);
  }

  public void suicide(DataWord obtainerAddress) {

    byte[] owner = TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes());
    byte[] obtainer = TransactionTrace.convertToStabilaAddress(obtainerAddress.getLast20Bytes());

    if (VMConfig.allowSvmVote()) {
      withdrawRewardAndCancelVote(owner, getContractState());
    }

    long balance = getContractState().getBalance(owner);

    if (logger.isDebugEnabled()) {
      logger.debug("Transfer to: [{}] heritage: [{}]",
          Hex.toHexString(obtainer),
          balance);
    }

    increaseNonce();

    addInternalTx(null, owner, obtainer, balance, null, "suicide", nonce,
        getContractState().getAccount(owner).getAssetMapV2());

    if (FastByteComparisons.compareTo(owner, 0, 20, obtainer, 0, 20) == 0) {
      // if owner == obtainer just zeroing account according to Yellow Paper
      getContractState().addBalance(owner, -balance);
      byte[] blackHoleAddress = getContractState().getBlackHoleAddress();
      if (VMConfig.allowSvmTransferSrc10()) {
        getContractState().addBalance(blackHoleAddress, balance);
        MUtil.transferAllToken(getContractState(), owner, blackHoleAddress);
      }
    } else {
      createAccountIfNotExist(getContractState(), obtainer);
      try {
        MUtil.transfer(getContractState(), owner, obtainer, balance);
        if (VMConfig.allowSvmTransferSrc10()) {
          MUtil.transferAllToken(getContractState(), owner, obtainer);
        }
      } catch (ContractValidateException e) {
        if (VMConfig.allowSvmConstantinople()) {
          throw new TransferException(
              "transfer all token or transfer all stb failed in suicide: %s", e.getMessage());
        }
        throw new BytecodeExecutionException("transfer failure");
      }
    }
    if (VMConfig.allowSvmCd()) {
      byte[] blackHoleAddress = getContractState().getBlackHoleAddress();
      if (FastByteComparisons.isEqual(owner, obtainer)) {
        transferDelegatedResourceToInheritor(owner, blackHoleAddress, getContractState());
      } else {
        transferDelegatedResourceToInheritor(owner, obtainer, getContractState());
      }
    }
    getResult().addDeleteAccount(this.getContractAddress());
  }

  public Repository getContractState() {
    return this.contractState;
  }

  private void transferDelegatedResourceToInheritor(byte[] ownerAddr, byte[] inheritorAddr, Repository repo) {

    // delegated resource from sender to owner, just abandon
    // in order to making that sender can uncd their balance in future
    // nothing will be deleted

    // delegated resource from owner to receiver
    // there cannot be any resource when suicide

    AccountCapsule ownerCapsule = repo.getAccount(ownerAddr);

    // transfer owner`s cded balance for bandwidth to inheritor
    long cdedBalanceForBandwidthOfOwner = 0;
    // check if cded for bandwidth exists
    if (ownerCapsule.getCdedCount() != 0) {
      cdedBalanceForBandwidthOfOwner = ownerCapsule.getCdedList().get(0).getCdedBalance();
    }
    repo.addTotalNetWeight(-cdedBalanceForBandwidthOfOwner / Parameter.ChainConstant.STB_PRECISION);

    long cdedBalanceForUcrOfOwner =
        ownerCapsule.getAccountResource().getCdedBalanceForUcr().getCdedBalance();
    repo.addTotalUcrWeight(-cdedBalanceForUcrOfOwner / Parameter.ChainConstant.STB_PRECISION);

    // transfer all kinds of cded balance to BlackHole
    repo.addBalance(inheritorAddr, cdedBalanceForBandwidthOfOwner + cdedBalanceForUcrOfOwner);
  }

  private void withdrawRewardAndCancelVote(byte[] owner, Repository repo) {
    VoteRewardUtil.withdrawReward(owner, repo);

    AccountCapsule ownerCapsule = repo.getAccount(owner);
    if (!ownerCapsule.getVotesList().isEmpty()) {
      VotesCapsule votesCapsule = repo.getVotes(owner);
      if (votesCapsule == null) {
        votesCapsule = new VotesCapsule(ByteString.copyFrom(owner),
            ownerCapsule.getVotesList());
      } else {
        votesCapsule.clearNewVotes();
      }
      ownerCapsule.clearVotes();
      repo.updateVotes(owner, votesCapsule);
    }
    try {
      long balance = ownerCapsule.getBalance();
      long allowance = ownerCapsule.getAllowance();
      ownerCapsule.setInstance(ownerCapsule.getInstance().toBuilder()
          .setBalance(Math.addExact(balance, allowance))
          .setAllowance(0)
          .setLatestWithdrawTime(getTimestamp().longValue() * 1000)
          .build());
      repo.updateAccount(ownerCapsule.createDbKey(), ownerCapsule);
    } catch (ArithmeticException e) {
      throw new BytecodeExecutionException("Suicide: balance and allowance out of long range.");
    }
  }

  public boolean canSuicide() {
    byte[] owner = TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes());
    AccountCapsule accountCapsule = getContractState().getAccount(owner);
    return !VMConfig.allowSvmCd()
        || (accountCapsule.getDelegatedCdedBalanceForBandwidth() == 0
        && accountCapsule.getDelegatedCdedBalanceForUcr() == 0);
//    boolean voteCheck = !VMConfig.allowSvmVote()
//        || (accountCapsule.getVotesList().size() == 0
//        && VoteRewardUtil.queryReward(owner, getContractState()) == 0
//        && getContractState().getAccountVote(
//            getContractState().getBeginCycle(owner), owner) == null);
//    return cdCheck && voteCheck;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  public void createContract(DataWord value, DataWord memStart, DataWord memSize) {
    returnDataBuffer = null; // reset return buffer right before the call

    if (getCallDeep() == MAX_DEPTH) {
      stackPushZero();
      return;
    }
    // [1] FETCH THE CODE FROM THE MEMORY
    byte[] programCode = memoryChunk(memStart.intValue(), memSize.intValue());

    byte[] newAddress = TransactionUtil
        .generateContractAddress(rootTransactionId, nonce);

    createContractImpl(value, programCode, newAddress, false);
  }

  private void createContractImpl(DataWord value, byte[] programCode, byte[] newAddress,
      boolean isCreate2) {
    byte[] senderAddress = TransactionTrace
        .convertToStabilaAddress(this.getContractAddress().getLast20Bytes());

    if (logger.isDebugEnabled()) {
      logger.debug("creating a new contract inside contract run: [{}]",
          Hex.toHexString(senderAddress));
    }

    long endowment = value.value().longValueExact();
    if (getContractState().getBalance(senderAddress) < endowment) {
      stackPushZero();
      return;
    }

    AccountCapsule existingAccount = getContractState().getAccount(newAddress);
    boolean contractAlreadyExists = existingAccount != null;

    if (VMConfig.allowSvmConstantinople()) {
      contractAlreadyExists =
          contractAlreadyExists && isContractExist(existingAccount, getContractState());
    }
    Repository deposit = getContractState().newRepositoryChild();
    if (VMConfig.allowSvmConstantinople()) {
      if (existingAccount == null) {
        deposit.createAccount(newAddress, "CreatedByContract",
            AccountType.Contract);
      } else if (!contractAlreadyExists) {
        existingAccount.updateAccountType(AccountType.Contract);
        existingAccount.clearDelegatedResource();
        deposit.updateAccount(newAddress, existingAccount);
      }

      if (!contractAlreadyExists) {
        Builder builder = SmartContract.newBuilder();
        builder.setContractAddress(ByteString.copyFrom(newAddress))
            .setConsumeUserResourcePercent(100)
            .setOriginAddress(ByteString.copyFrom(senderAddress));
        if (isCreate2) {
          builder.setStbHash(ByteString.copyFrom(rootTransactionId));
        }
        SmartContract newSmartContract = builder.build();
        deposit.createContract(newAddress, new ContractCapsule(newSmartContract));
      }
    } else {
      deposit.createAccount(newAddress, "CreatedByContract",
          Protocol.AccountType.Contract);
      SmartContract newSmartContract = SmartContract.newBuilder()
          .setContractAddress(ByteString.copyFrom(newAddress)).setConsumeUserResourcePercent(100)
          .setOriginAddress(ByteString.copyFrom(senderAddress)).build();
      deposit.createContract(newAddress, new ContractCapsule(newSmartContract));
      // In case of hashing collisions, check for any balance before createAccount()
      long oldBalance = deposit.getBalance(newAddress);
      deposit.addBalance(newAddress, oldBalance);
    }

    // [4] TRANSFER THE BALANCE
    long newBalance = 0L;
    if (!byTestingSuite() && endowment > 0) {
      try {
        VMUtils.validateForSmartContract(deposit, senderAddress, newAddress, endowment);
      } catch (ContractValidateException e) {
        // TODO: unreachable exception
        throw new BytecodeExecutionException(VALIDATE_FOR_SMART_CONTRACT_FAILURE, e.getMessage());
      }
      deposit.addBalance(senderAddress, -endowment);
      newBalance = deposit.addBalance(newAddress, endowment);
    }

    // actual ucr subtract
    DataWord ucrLimit = this.getCreateUcr(getUcrLimitLeft());
    spendUcr(ucrLimit.longValue(), "internal call");

    increaseNonce();
    // [5] COOK THE INVOKE AND EXECUTE
    InternalTransaction internalTx = addInternalTx(null, senderAddress, newAddress, endowment,
        programCode, "create", nonce, null);
    long vmStartInUs = System.nanoTime() / 1000;
    ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(
        this, new DataWord(newAddress), getContractAddress(), value, new DataWord(0),
        new DataWord(0),
        newBalance, null, deposit, false, byTestingSuite(), vmStartInUs,
        getVmShouldEndInUs(), ucrLimit.longValueSafe());
    if (isConstantCall()) {
      programInvoke.setConstantCall();
    }
    ProgramResult createResult = ProgramResult.createEmpty();

    if (contractAlreadyExists) {
      createResult.setException(new BytecodeExecutionException(
          "Trying to create a contract with existing contract address: 0x" + Hex
              .toHexString(newAddress)));
    } else if (isNotEmpty(programCode)) {
      VM vm = new VM(config);
      Program program = new Program(programCode, programInvoke, internalTx, config);
      program.setRootTransactionId(this.rootTransactionId);
      vm.play(program);
      createResult = program.getResult();
      getTrace().merge(program.getTrace());
      // always commit nonce
      this.nonce = program.nonce;

    }

    // 4. CREATE THE CONTRACT OUT OF RETURN
    byte[] code = createResult.getHReturn();

    long saveCodeUcr = (long) getLength(code) * UcrCost.getInstance().getCREATE_DATA();

    long afterSpend =
        programInvoke.getUcrLimit() - createResult.getUcrUsed() - saveCodeUcr;
    if (!createResult.isRevert()) {
      if (afterSpend < 0) {
        createResult.setException(
            Exception.notEnoughSpendUcr("No ucr to save just created contract code",
                saveCodeUcr, programInvoke.getUcrLimit() - createResult.getUcrUsed()));
      } else {
        createResult.spendUcr(saveCodeUcr);
        deposit.saveCode(newAddress, code);
      }
    }

    getResult().merge(createResult);

    if (createResult.getException() != null || createResult.isRevert()) {
      logger.debug("contract run halted by Exception: contract: [{}], exception: [{}]",
          Hex.toHexString(newAddress),
          createResult.getException());

      if(internalTx != null){
        internalTx.reject();
      }

      createResult.rejectInternalTransactions();

      stackPushZero();

      if (createResult.getException() != null) {
        return;
      } else {
        returnDataBuffer = createResult.getHReturn();
      }
    } else {
      if (!byTestingSuite()) {
        deposit.commit();
      }

      // IN SUCCESS PUSH THE ADDRESS INTO THE STACK
      stackPush(new DataWord(newAddress));
    }

    // 5. REFUND THE REMAIN Ucr
    refundUcrAfterVM(ucrLimit, createResult);
  }

  public void refundUcrAfterVM(DataWord ucrLimit, ProgramResult result) {

    long refundUcr = ucrLimit.longValueSafe() - result.getUcrUsed();
    if (refundUcr > 0) {
      refundUcr(refundUcr, "remain ucr from the internal call");
      if (logger.isDebugEnabled()) {
        logger.debug("The remaining ucr is refunded, account: [{}], ucr: [{}] ",
            Hex.toHexString(
                TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes())),
            refundUcr);
      }
    }
  }

  /**
   * . That method is for internal code invocations
   * <p/>
   * - Normal calls invoke a specified contract which updates itself - Stateless calls invoke code
   * from another contract, within the context of the caller
   *
   * @param msg is the message call object
   */
  public void callToAddress(MessageCall msg) {
    returnDataBuffer = null; // reset return buffer right before the call

    if (getCallDeep() == MAX_DEPTH) {
      stackPushZero();
      refundUcr(msg.getUcr().longValue(), " call deep limit reach");
      return;
    }

    byte[] data = memoryChunk(msg.getInDataOffs().intValue(), msg.getInDataSize().intValue());

    // FETCH THE SAVED STORAGE
    byte[] codeAddress = TransactionTrace
        .convertToStabilaAddress(msg.getCodeAddress().getLast20Bytes());
    byte[] senderAddress = TransactionTrace
        .convertToStabilaAddress(getContractAddress().getLast20Bytes());
    byte[] contextAddress = msg.getType().callIsStateless() ? senderAddress : codeAddress;

    if (logger.isDebugEnabled()) {
      logger.debug(msg.getType().name()
              + " for existing contract: address: [{}], outDataOffs: [{}], outDataSize: [{}]  ",
          Hex.toHexString(contextAddress), msg.getOutDataOffs().longValue(),
          msg.getOutDataSize().longValue());
    }

    Repository deposit = getContractState().newRepositoryChild();

    // 2.1 PERFORM THE VALUE (endowment) PART
    long endowment;
    try {
      endowment = msg.getEndowment().value().longValueExact();
    } catch (ArithmeticException e) {
      if (VMConfig.allowSvmConstantinople()) {
        refundUcr(msg.getUcr().longValue(), "endowment out of long range");
        throw new TransferException("endowment out of long range");
      } else {
        throw e;
      }
    }
    // transfer STB validation
    byte[] tokenId = null;

    checkTokenId(msg);

    boolean isTokenTransfer = isTokenTransfer(msg);

    if (!isTokenTransfer) {
      long senderBalance = deposit.getBalance(senderAddress);
      if (senderBalance < endowment) {
        stackPushZero();
        refundUcr(msg.getUcr().longValue(), REFUND_UCR_FROM_MESSAGE_CALL);
        return;
      }
    } else {
      // transfer src10 token validation
      tokenId = String.valueOf(msg.getTokenId().longValue()).getBytes();
      long senderBalance = deposit.getTokenBalance(senderAddress, tokenId);
      if (senderBalance < endowment) {
        stackPushZero();
        refundUcr(msg.getUcr().longValue(), REFUND_UCR_FROM_MESSAGE_CALL);
        return;
      }
    }

    // FETCH THE CODE
    AccountCapsule accountCapsule = getContractState().getAccount(codeAddress);

    byte[] programCode =
        accountCapsule != null ? getContractState().getCode(codeAddress) : EMPTY_BYTE_ARRAY;

    // only for STB, not for token
    long contextBalance = 0L;
    if (byTestingSuite()) {
      // This keeps track of the calls created for a test
      getResult().addCallCreate(data, contextAddress,
          msg.getUcr().getNoLeadZeroesData(),
          msg.getEndowment().getNoLeadZeroesData());
    } else if (!ArrayUtils.isEmpty(senderAddress) && !ArrayUtils.isEmpty(contextAddress)
        && senderAddress != contextAddress && endowment > 0) {
      createAccountIfNotExist(deposit, contextAddress);
      if (!isTokenTransfer) {
        try {
          VMUtils
              .validateForSmartContract(deposit, senderAddress, contextAddress, endowment);
        } catch (ContractValidateException e) {
          if (VMConfig.allowSvmConstantinople()) {
            refundUcr(msg.getUcr().longValue(), REFUND_UCR_FROM_MESSAGE_CALL);
            throw new TransferException("transfer stb failed: %s", e.getMessage());
          }
          throw new BytecodeExecutionException(VALIDATE_FOR_SMART_CONTRACT_FAILURE, e.getMessage());
        }
        deposit.addBalance(senderAddress, -endowment);
        contextBalance = deposit.addBalance(contextAddress, endowment);
      } else {
        try {
          VMUtils.validateForSmartContract(deposit, senderAddress, contextAddress,
              tokenId, endowment);
        } catch (ContractValidateException e) {
          if (VMConfig.allowSvmConstantinople()) {
            refundUcr(msg.getUcr().longValue(), REFUND_UCR_FROM_MESSAGE_CALL);
            throw new TransferException("transfer src10 failed: %s", e.getMessage());
          }
          throw new BytecodeExecutionException(VALIDATE_FOR_SMART_CONTRACT_FAILURE, e.getMessage());
        }
        deposit.addTokenBalance(senderAddress, tokenId, -endowment);
        deposit.addTokenBalance(contextAddress, tokenId, endowment);
      }
    }

    // CREATE CALL INTERNAL TRANSACTION
    increaseNonce();
    HashMap<String, Long> tokenInfo = new HashMap<>();
    if (isTokenTransfer) {
      tokenInfo.put(new String(stripLeadingZeroes(tokenId)), endowment);
    }
    InternalTransaction internalTx = addInternalTx(null, senderAddress, contextAddress,
        !isTokenTransfer ? endowment : 0, data, "call", nonce,
        !isTokenTransfer ? null : tokenInfo);
    ProgramResult callResult = null;
    if (isNotEmpty(programCode)) {
      long vmStartInUs = System.nanoTime() / 1000;
      DataWord callValue = msg.getType().callIsDelegate() ? getCallValue() : msg.getEndowment();
      ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(
          this, new DataWord(contextAddress),
          msg.getType().callIsDelegate() ? getCallerAddress() : getContractAddress(),
          !isTokenTransfer ? callValue : new DataWord(0),
          !isTokenTransfer ? new DataWord(0) : callValue,
          !isTokenTransfer ? new DataWord(0) : msg.getTokenId(),
          contextBalance, data, deposit, msg.getType().callIsStatic() || isStaticCall(),
          byTestingSuite(), vmStartInUs, getVmShouldEndInUs(), msg.getUcr().longValueSafe());
      if (isConstantCall()) {
        programInvoke.setConstantCall();
      }
      VM vm = new VM(config);
      Program program = new Program(programCode, programInvoke, internalTx, config);
      program.setRootTransactionId(this.rootTransactionId);
      vm.play(program);
      callResult = program.getResult();

      getTrace().merge(program.getTrace());
      getResult().merge(callResult);
      // always commit nonce
      this.nonce = program.nonce;

      if (callResult.getException() != null || callResult.isRevert()) {
        logger.debug("contract run halted by Exception: contract: [{}], exception: [{}]",
            Hex.toHexString(contextAddress),
            callResult.getException());

        if(internalTx != null){
          internalTx.reject();
        }

        callResult.rejectInternalTransactions();

        stackPushZero();

        if (callResult.getException() != null) {
          return;
        }
      } else {
        // 4. THE FLAG OF SUCCESS IS ONE PUSHED INTO THE STACK
        deposit.commit();
        stackPushOne();
      }

      if (byTestingSuite()) {
        logger.debug("Testing run, skipping storage diff listener");
      }
    } else {
      // 4. THE FLAG OF SUCCESS IS ONE PUSHED INTO THE STACK
      deposit.commit();
      stackPushOne();
    }

    // 3. APPLY RESULTS: result.getHReturn() into out_memory allocated
    if (callResult != null) {
      byte[] buffer = callResult.getHReturn();
      int offset = msg.getOutDataOffs().intValue();
      int size = msg.getOutDataSize().intValue();

      memorySaveLimited(offset, buffer, size);

      returnDataBuffer = buffer;
    }

    // 5. REFUND THE REMAIN UCR
    if (callResult != null) {
      BigInteger refundUcr = msg.getUcr().value()
          .subtract(BIUtil.toBI(callResult.getUcrUsed()));
      if (BIUtil.isPositive(refundUcr)) {
        refundUcr(refundUcr.longValueExact(), "remaining ucr from the internal call");
        if (logger.isDebugEnabled()) {
          logger.debug("The remaining ucr refunded, account: [{}], ucr: [{}] ",
              Hex.toHexString(senderAddress),
              refundUcr.toString());
        }
      }
    } else {
      refundUcr(msg.getUcr().longValue(), "remaining ucr from the internal call");
    }
  }

  public void increaseNonce() {
    nonce++;
  }

  public void resetNonce() {
    nonce = 0;
  }

  public void spendUcr(long ucrValue, String opName) {
    if (getUcrlimitLeftLong() < ucrValue) {
      throw new OutOfUcrException(
          "Not enough ucr for '%s' operation executing: curInvokeUcrLimit[%d],"
              + " curOpUcr[%d], usedUcr[%d]",
          opName, invoke.getUcrLimit(), ucrValue, getResult().getUcrUsed());
    }
    getResult().spendUcr(ucrValue);
  }

  public void checkCPUTimeLimit(String opName) {

    if (CommonParameter.getInstance().isDebug()) {
      return;
    }
    if (CommonParameter.getInstance().isSolidityNode()) {
      return;
    }
    long vmNowInUs = System.nanoTime() / 1000;
    if (vmNowInUs > getVmShouldEndInUs()) {
      logger.info(
          "minTimeRatio: {}, maxTimeRatio: {}, vm should end time in us: {}, "
              + "vm now time in us: {}, vm start time in us: {}",
          CommonParameter.getInstance().getMinTimeRatio(),
          CommonParameter.getInstance().getMaxTimeRatio(),
          getVmShouldEndInUs(), vmNowInUs, getVmStartInUs());
      throw Exception.notEnoughTime(opName);
    }

  }

  public void spendAllUcr() {
    spendUcr(getUcrLimitLeft().longValue(), "Spending all remaining");
  }

  public void refundUcr(long ucrValue, String cause) {
    logger
        .debug("[{}] Refund for cause: [{}], ucr: [{}]", invoke.hashCode(), cause, ucrValue);
    getResult().refundUcr(ucrValue);
  }

  public void futureRefundUcr(long ucrValue) {
    logger.debug("Future refund added: [{}]", ucrValue);
    getResult().addFutureRefund(ucrValue);
  }

  public void resetFutureRefund() {
    getResult().resetFutureRefund();
  }

  public void storageSave(DataWord word1, DataWord word2) {
    DataWord keyWord = word1.clone();
    DataWord valWord = word2.clone();
    getContractState()
        .putStorageValue(
            TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes()), keyWord,
            valWord);
  }

  public byte[] getCode() {
    return ops.clone();
  }

  public byte[] getCodeAt(DataWord address) {
    byte[] code = invoke.getDeposit()
        .getCode(TransactionTrace.convertToStabilaAddress(address.getLast20Bytes()));
    return nullToEmpty(code);
  }

  public byte[] getCodeHashAt(DataWord address) {
    byte[] stabilaAddr = TransactionTrace.convertToStabilaAddress(address.getLast20Bytes());
    AccountCapsule account = getContractState().getAccount(stabilaAddr);
    if (account != null) {
      ContractCapsule contract = getContractState().getContract(stabilaAddr);
      byte[] codeHash;
      if (contract != null) {
        codeHash = contract.getCodeHash();
        if (ByteUtil.isNullOrZeroArray(codeHash)) {
          byte[] code = getCodeAt(address);
          codeHash = Hash.sha3(code);
          contract.setCodeHash(codeHash);
          getContractState().updateContract(stabilaAddr, contract);
        }
      } else {
        codeHash = Hash.sha3(new byte[0]);
      }
      return codeHash;
    } else {
      return EMPTY_BYTE_ARRAY;
    }
  }

  public DataWord getContractAddress() {
    return invoke.getContractAddress().clone();
  }

  public DataWord getBlockHash(int index) {
    if (index < this.getNumber().longValue()
        && index >= Math.max(256, this.getNumber().longValue()) - 256) {

      BlockCapsule blockCapsule = contractState.getBlockByNum(index);

      if (Objects.nonNull(blockCapsule)) {
        return new DataWord(blockCapsule.getBlockId().getBytes()).clone();
      } else {
        return DataWord.ZERO.clone();
      }
    } else {
      return DataWord.ZERO.clone();
    }

  }

  public DataWord getBalance(DataWord address) {
    long balance = getContractState()
        .getBalance(TransactionTrace.convertToStabilaAddress(address.getLast20Bytes()));
    return new DataWord(balance);
  }

  public DataWord getRewardBalance(DataWord address) {
    long rewardBalance = VoteRewardUtil.queryReward(
        TransactionTrace.convertToStabilaAddress(address.getLast20Bytes()), getContractState());
    return new DataWord(rewardBalance);
  }

  public DataWord isContract(DataWord address) {
    ContractCapsule contract = getContractState()
        .getContract(TransactionTrace.convertToStabilaAddress(address.getLast20Bytes()));
    return contract != null ? new DataWord(1) : new DataWord(0);
  }

  public DataWord isSRCandidate(DataWord address) {
    ExecutiveCapsule executiveCapsule = getContractState()
            .getExecutive(TransactionTrace.convertToStabilaAddress(address.getLast20Bytes()));
    return executiveCapsule != null ? new DataWord(1) : new DataWord(0);
  }

  public DataWord getOriginAddress() {
    return invoke.getOriginAddress().clone();
  }

  public DataWord getCallerAddress() {
    return invoke.getCallerAddress().clone();
  }

  public DataWord getChainId() {
    return new DataWord(Hex.toHexString(getContractState()
        .getBlockByNum(0).getBlockId().getBytes()));
  }
  public DataWord getDropPrice() {
    return new DataWord(1);
  }

  public long getUcrlimitLeftLong() {
    return invoke.getUcrLimit() - getResult().getUcrUsed();
  }

  public DataWord getUcrLimitLeft() {
    return new DataWord(invoke.getUcrLimit() - getResult().getUcrUsed());
  }

  public long getVmShouldEndInUs() {
    return invoke.getVmShouldEndInUs();
  }

  public DataWord getCallValue() {
    return invoke.getCallValue().clone();
  }

  public DataWord getDataSize() {
    return invoke.getDataSize().clone();
  }

  public DataWord getDataValue(DataWord index) {
    return invoke.getDataValue(index);
  }

  public byte[] getDataCopy(DataWord offset, DataWord length) {
    return invoke.getDataCopy(offset, length);
  }

  public DataWord getReturnDataBufferSize() {
    return new DataWord(getReturnDataBufferSizeI());
  }

  private int getReturnDataBufferSizeI() {
    return returnDataBuffer == null ? 0 : returnDataBuffer.length;
  }

  public byte[] getReturnDataBufferData(DataWord off, DataWord size) {
    if ((long) off.intValueSafe() + size.intValueSafe() > getReturnDataBufferSizeI()) {
      return null;
    }
    return returnDataBuffer == null ? new byte[0] :
        Arrays.copyOfRange(returnDataBuffer, off.intValueSafe(),
            off.intValueSafe() + size.intValueSafe());
  }

  public DataWord storageLoad(DataWord key) {
    DataWord ret = getContractState()
        .getStorageValue(
            TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes()),
            key.clone());
    return ret == null ? null : ret.clone();
  }

  public DataWord getTokenBalance(DataWord address, DataWord tokenId) {
    checkTokenIdInTokenBalance(tokenId);
    long ret = getContractState()
        .getTokenBalance(TransactionTrace.convertToStabilaAddress(address.getLast20Bytes()),
            String.valueOf(tokenId.longValue()).getBytes());
    return ret == 0 ? new DataWord(0) : new DataWord(ret);
  }

  public DataWord getTokenValue() {
    return invoke.getTokenValue().clone();
  }

  public DataWord getTokenId() {
    return invoke.getTokenId().clone();
  }

  public DataWord getPrevHash() {
    return invoke.getPrevHash().clone();
  }

  public DataWord getCoinbase() {
    return invoke.getCoinbase().clone();
  }

  public DataWord getTimestamp() {
    return invoke.getTimestamp().clone();
  }

  public DataWord getNumber() {
    return invoke.getNumber().clone();
  }

  public DataWord getDifficulty() {
    return invoke.getDifficulty().clone();
  }

  public boolean isStaticCall() {
    return invoke.isStaticCall();
  }

  public boolean isConstantCall() {
    return invoke.isConstantCall();
  }

  public ProgramResult getResult() {
    return result;
  }

  public void setRuntimeFailure(RuntimeException e) {
    getResult().setException(e);
  }

  public String memoryToString() {
    return memory.toString();
  }

  public void fullTrace() {
    if (logger.isTraceEnabled() || listener != null) {

      StringBuilder stackData = new StringBuilder();
      for (int i = 0; i < stack.size(); ++i) {
        stackData.append(" ").append(stack.get(i));
        if (i < stack.size() - 1) {
          stackData.append("\n");
        }
      }

      if (stackData.length() > 0) {
        stackData.insert(0, "\n");
      }

      StringBuilder memoryData = new StringBuilder();
      StringBuilder oneLine = new StringBuilder();
      if (memory.size() > 320) {
        memoryData.append("... Memory Folded.... ")
            .append("(")
            .append(memory.size())
            .append(") bytes");
      } else {
        for (int i = 0; i < memory.size(); ++i) {

          byte value = memory.readByte(i);
          oneLine.append(ByteUtil.oneByteToHexString(value)).append(" ");

          if ((i + 1) % 16 == 0) {
            String tmp = format("[%4s]-[%4s]", Integer.toString(i - 15, 16),
                Integer.toString(i, 16)).replace(" ", "0");
            memoryData.append("").append(tmp).append(" ");
            memoryData.append(oneLine);
            if (i < memory.size()) {
              memoryData.append("\n");
            }
            oneLine.setLength(0);
          }
        }
      }
      if (memoryData.length() > 0) {
        memoryData.insert(0, "\n");
      }

      StringBuilder opsString = new StringBuilder();
      for (int i = 0; i < ops.length; ++i) {

        String tmpString = Integer.toString(ops[i] & 0xFF, 16);
        tmpString = tmpString.length() == 1 ? "0" + tmpString : tmpString;

        if (i != pc) {
          opsString.append(tmpString);
        } else {
          opsString.append(" >>").append(tmpString).append("");
        }

      }
      if (pc >= ops.length) {
        opsString.append(" >>");
      }
      if (opsString.length() > 0) {
        opsString.insert(0, "\n ");
      }

      logger.trace(" -- OPS --     {}", opsString);
      logger.trace(" -- STACK --   {}", stackData);
      logger.trace(" -- MEMORY --  {}", memoryData);
      logger.trace("\n  Spent Drop: [{}]/[{}]\n  Left Ucr:  [{}]\n",
          getResult().getUcrUsed(),
          invoke.getUcrLimit(),
          getUcrLimitLeft().longValue());

      StringBuilder globalOutput = new StringBuilder("\n");
      if (stackData.length() > 0) {
        stackData.append("\n");
      }

      if (pc != 0) {
        globalOutput.append("[Op: ").append(OpCode.code(lastOp).name()).append("]\n");
      }

      globalOutput.append(" -- OPS --     ").append(opsString).append("\n");
      globalOutput.append(" -- STACK --   ").append(stackData).append("\n");
      globalOutput.append(" -- MEMORY --  ").append(memoryData).append("\n");

      if (getResult().getHReturn() != null) {
        globalOutput.append("\n  HReturn: ").append(
            Hex.toHexString(getResult().getHReturn()));
      }

      // sophisticated assumption that msg.data != codedata
      // means we are calling the contract not creating it
      byte[] txData = invoke.getDataCopy(DataWord.ZERO, getDataSize());
      if (!Arrays.equals(txData, ops)) {
        globalOutput.append("\n  msg.data: ").append(Hex.toHexString(txData));
      }
      globalOutput.append("\n\n  Spent Ucr: ").append(getResult().getUcrUsed());

      if (listener != null) {
        listener.output(globalOutput.toString());
      }
    }
  }

  public void saveOpTrace() {
    if (this.pc < ops.length) {
      trace.addOp(ops[pc], pc, getCallDeep(), getUcrLimitLeft(), traceListener.resetActions());
    }
  }

  public ProgramTrace getTrace() {
    return trace;
  }

  public void createContract2(DataWord value, DataWord memStart, DataWord memSize, DataWord salt) {
    byte[] senderAddress;
    if(VMConfig.allowSvmIstanbul()) {
      senderAddress = TransactionTrace
          .convertToStabilaAddress(this.getContractAddress().getLast20Bytes());
    } else {
      senderAddress = TransactionTrace
          .convertToStabilaAddress(this.getCallerAddress().getLast20Bytes());
    }
    byte[] programCode = memoryChunk(memStart.intValue(), memSize.intValue());

    byte[] contractAddress = WalletUtil
        .generateContractAddress2(senderAddress, salt.getData(), programCode);
    createContractImpl(value, programCode, contractAddress, true);
  }

  public void addListener(ProgramOutListener listener) {
    this.listener = listener;
  }

  public int verifyJumpDest(DataWord nextPC) {
    if (nextPC.bytesOccupied() > 4) {
      throw Exception.badJumpDestination(-1);
    }
    int ret = nextPC.intValue();
    if (!getProgramPrecompile().hasJumpDest(ret)) {
      throw Exception.badJumpDestination(ret);
    }
    return ret;
  }

  public void callToPrecompiledAddress(MessageCall msg,
      PrecompiledContracts.PrecompiledContract contract) {
    returnDataBuffer = null; // reset return buffer right before the call

    if (getCallDeep() == MAX_DEPTH) {
      stackPushZero();
      this.refundUcr(msg.getUcr().longValue(), " call deep limit reach");
      return;
    }

    Repository deposit = getContractState().newRepositoryChild();

    byte[] senderAddress = TransactionTrace
        .convertToStabilaAddress(this.getContractAddress().getLast20Bytes());
    byte[] codeAddress = TransactionTrace
        .convertToStabilaAddress(msg.getCodeAddress().getLast20Bytes());
    byte[] contextAddress = msg.getType().callIsStateless() ? senderAddress : codeAddress;

    long endowment = msg.getEndowment().value().longValueExact();
    long senderBalance = 0;
    byte[] tokenId = null;

    checkTokenId(msg);
    boolean isTokenTransfer = isTokenTransfer(msg);
    // transfer STB validation
    if (!isTokenTransfer) {
      senderBalance = deposit.getBalance(senderAddress);
    } else {
      // transfer src10 token validation
      tokenId = String.valueOf(msg.getTokenId().longValue()).getBytes();
      senderBalance = deposit.getTokenBalance(senderAddress, tokenId);
    }
    if (senderBalance < endowment) {
      stackPushZero();
      refundUcr(msg.getUcr().longValue(), REFUND_UCR_FROM_MESSAGE_CALL);
      return;
    }
    byte[] data = this.memoryChunk(msg.getInDataOffs().intValue(),
        msg.getInDataSize().intValue());

    // Charge for endowment - is not reversible by rollback
    if (!ArrayUtils.isEmpty(senderAddress) && !ArrayUtils.isEmpty(contextAddress)
        && senderAddress != contextAddress && msg.getEndowment().value().longValueExact() > 0) {
      if (!isTokenTransfer) {
        try {
          MUtil.transfer(deposit, senderAddress, contextAddress,
              msg.getEndowment().value().longValueExact());
        } catch (ContractValidateException e) {
          throw new BytecodeExecutionException("transfer failure");
        }
      } else {
        try {
          VMUtils
              .validateForSmartContract(deposit, senderAddress, contextAddress, tokenId, endowment);
        } catch (ContractValidateException e) {
          throw new BytecodeExecutionException(VALIDATE_FOR_SMART_CONTRACT_FAILURE, e.getMessage());
        }
        deposit.addTokenBalance(senderAddress, tokenId, -endowment);
        deposit.addTokenBalance(contextAddress, tokenId, endowment);
      }
    }

    long requiredUcr = contract.getUcrForData(data);
    if (requiredUcr > msg.getUcr().longValue()) {
      // Not need to throw an exception, method caller needn't know that
      // regard as consumed the ucr
      this.refundUcr(0, CALL_PRE_COMPILED); //matches cpp logic
      this.stackPushZero();
    } else {
      // Delegate or not. if is delegated, we will use msg sender, otherwise use contract address
      contract.setCallerAddress(TransactionTrace.convertToStabilaAddress(msg.getType().callIsDelegate()
          ? getCallerAddress().getLast20Bytes() : getContractAddress().getLast20Bytes()));
      // this is the depositImpl, not contractState as above
      contract.setRepository(deposit);
      contract.setResult(this.result);
      contract.setConstantCall(isConstantCall());
      contract.setVmShouldEndInUs(getVmShouldEndInUs());
      Pair<Boolean, byte[]> out = contract.execute(data);

      if (out.getLeft()) { // success
        this.refundUcr(msg.getUcr().longValue() - requiredUcr, CALL_PRE_COMPILED);
        this.stackPushOne();
        returnDataBuffer = out.getRight();
        deposit.commit();
      } else {
        // spend all ucr on failure, push zero and revert state changes
        this.refundUcr(0, CALL_PRE_COMPILED);
        this.stackPushZero();
        if (Objects.nonNull(this.result.getException())) {
          throw result.getException();
        }
      }

      this.memorySave(msg.getOutDataOffs().intValue(), out.getRight());
    }
  }

  public boolean byTestingSuite() {
    return invoke.byTestingSuite();
  }

  /**
   * check TokenId TokenId  \ isTransferToken -------------------------------------------------------------------
   * false                                     true -----------------------------------------------
   * (-∞,Long.Min)        Not possible            error: msg.getTokenId().value().longValueExact()
   * ---------------------------------------------------------------------------------------------
   * [Long.Min, 0)        Not possible                               error
   * --------------------------------------------------------------------------------------------- 0
   * allowed and only allowed                    error (guaranteed in CALLTOKEN) transfertoken id=0
   * should not transfer STB） ---------------------------------------------------------------------
   * (0-100_0000]          Not possible                              error
   * ---------------------------------------------------------------------------------------------
   * (100_0000, Long.Max]  Not possible                             allowed
   * ---------------------------------------------------------------------------------------------
   * (Long.Max,+∞)         Not possible          error: msg.getTokenId().value().longValueExact()
   * ---------------------------------------------------------------------------------------------
   */
  public void checkTokenId(MessageCall msg) {
    if (VMConfig.allowMultiSign()) { //allowMultiSign proposal
      // tokenid should not get Long type overflow
      long tokenId;
      try {
        tokenId = msg.getTokenId().sValue().longValueExact();
      } catch (ArithmeticException e) {
        if (VMConfig.allowSvmConstantinople()) {
          refundUcr(msg.getUcr().longValue(), REFUND_UCR_FROM_MESSAGE_CALL);
          throw new TransferException(VALIDATE_FOR_SMART_CONTRACT_FAILURE, INVALID_TOKEN_ID_MSG);
        }
        throw e;
      }
      // tokenId can only be 0 when isTokenTransferMsg is false
      // or tokenId can be (MIN_TOKEN_ID, Long.Max] when isTokenTransferMsg == true
      if ((tokenId <= VMConstant.MIN_TOKEN_ID && tokenId != 0)
          || (tokenId == 0 && msg.isTokenTransferMsg())) {
        // tokenId == 0 is a default value for token id DataWord.
        if (VMConfig.allowSvmConstantinople()) {
          refundUcr(msg.getUcr().longValue(), REFUND_UCR_FROM_MESSAGE_CALL);
          throw new TransferException(VALIDATE_FOR_SMART_CONTRACT_FAILURE, INVALID_TOKEN_ID_MSG);
        }
        throw new BytecodeExecutionException(
            String.format(VALIDATE_FOR_SMART_CONTRACT_FAILURE, INVALID_TOKEN_ID_MSG));
      }
    }
  }

  public boolean isTokenTransfer(MessageCall msg) {
    if (VMConfig.allowMultiSign()) { //allowMultiSign proposal
      return msg.isTokenTransferMsg();
    } else {
      return msg.getTokenId().longValue() != 0;
    }
  }

  public void checkTokenIdInTokenBalance(DataWord tokenIdDataWord) {
    if (VMConfig.allowMultiSign()) { //allowMultiSigns proposal
      // tokenid should not get Long type overflow
      long tokenId;
      try {
        tokenId = tokenIdDataWord.sValue().longValueExact();
      } catch (ArithmeticException e) {
        if (VMConfig.allowSvmConstantinople()) {
          throw new TransferException(VALIDATE_FOR_SMART_CONTRACT_FAILURE, INVALID_TOKEN_ID_MSG);
        }
        throw e;
      }

      // or tokenId can only be (MIN_TOKEN_ID, Long.Max]
      if (tokenId <= VMConstant.MIN_TOKEN_ID) {
        throw new BytecodeExecutionException(
            String.format(VALIDATE_FOR_SMART_CONTRACT_FAILURE, INVALID_TOKEN_ID_MSG));
      }
    }
  }

  public DataWord getCallUcr(OpCode op, DataWord requestedUcr, DataWord availableUcr) {
    return requestedUcr.compareTo(availableUcr) > 0 ? availableUcr : requestedUcr;
  }

  public DataWord getCreateUcr(DataWord availableUcr) {
    return availableUcr;
  }

  /**
   * . used mostly for testing reasons
   */
  public byte[] getMemory() {
    return memory.read(0, memory.size());
  }

  /**
   * . used mostly for testing reasons
   */
  public void initMem(byte[] data) {
    this.memory.write(0, data, data.length, false);
  }

  public long getVmStartInUs() {
    return this.invoke.getVmStartInUs();
  }

  private boolean isContractExist(AccountCapsule existingAddr, Repository deposit) {
    return deposit.getContract(existingAddr.getAddress().toByteArray()) != null;
  }

  private void createAccountIfNotExist(Repository deposit, byte[] contextAddress) {
    if (VMConfig.allowSvmSolidity059()) {
      //after solidity059 proposal , allow contract transfer src10 or STB to non-exist address(would create one)
      AccountCapsule sender = deposit.getAccount(contextAddress);
      if (sender == null) {
        deposit.createNormalAccount(contextAddress);
      }
    }
  }

  public interface ProgramOutListener {

    void output(String out);
  }

  static class ByteCodeIterator {

    private byte[] code;
    private int pc;

    public ByteCodeIterator(byte[] code) {
      this.code = code;
    }

    public int getPC() {
      return pc;
    }

    public void setPC(int pc) {
      this.pc = pc;
    }

    public OpCode getCurOpcode() {
      return pc < code.length ? OpCode.code(code[pc]) : null;
    }

    public boolean isPush() {
      return getCurOpcode() != null && getCurOpcode().name().startsWith("PUSH");
    }

    public byte[] getCurOpcodeArg() {
      if (isPush()) {
        int nPush = getCurOpcode().val() - OpCode.PUSH1.val() + 1;
        byte[] data = Arrays.copyOfRange(code, pc + 1, pc + nPush + 1);
        return data;
      } else {
        return new byte[0];
      }
    }

    public boolean next() {
      pc += 1 + getCurOpcodeArg().length;
      return pc < code.length;
    }
  }

  public boolean cd(DataWord receiverAddress, DataWord cdedBalance, DataWord resourceType) {
    Repository repository = getContractState().newRepositoryChild();
    byte[] owner = TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes());
    byte[] receiver = TransactionTrace.convertToStabilaAddress(receiverAddress.getLast20Bytes());

    increaseNonce();
    InternalTransaction internalTx = addInternalTx(null, owner, receiver,
        cdedBalance.longValue(), null,
        "cdFor" + convertResourceToString(resourceType), nonce, null);

    CdBalanceParam param = new CdBalanceParam();
    param.setOwnerAddress(owner);
    param.setReceiverAddress(receiver);
    boolean needCheckCdedTime = CommonParameter.getInstance()
        .getCheckCdedTime() == 1; // for test
    param.setCdedDuration(needCheckCdedTime ?
        repository.getDynamicPropertiesStore().getMinCdedTime() : 0);
    param.setResourceType(parseResourceCode(resourceType));
    try {
      CdBalanceProcessor processor = new CdBalanceProcessor();
      param.setCdedBalance(cdedBalance.sValue().longValueExact());
      processor.validate(param, repository);
      processor.execute(param, repository);
      repository.commit();
      return true;
    } catch (ContractValidateException e) {
      logger.error("SVM Cd: validate failure. Reason: {}", e.getMessage());
    } catch (ArithmeticException e) {
      logger.error("SVM Cd: cdedBalance out of long range.");
    }
    if (internalTx != null) {
      internalTx.reject();
    }
    return false;
  }

  public boolean uncd(DataWord receiverAddress, DataWord resourceType) {
    Repository repository = getContractState().newRepositoryChild();
    byte[] owner = TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes());
    byte[] receiver = TransactionTrace.convertToStabilaAddress(receiverAddress.getLast20Bytes());

    increaseNonce();
    InternalTransaction internalTx = addInternalTx(null, owner, receiver, 0, null,
        "uncdFor" + convertResourceToString(resourceType), nonce, null);

    UncdBalanceParam param = new UncdBalanceParam();
    param.setOwnerAddress(owner);
    param.setReceiverAddress(receiver);
    param.setResourceType(parseResourceCode(resourceType));
    try {
      UncdBalanceProcessor processor = new UncdBalanceProcessor();
      processor.validate(param, repository);
      long uncdBalance = processor.execute(param, repository);
      repository.commit();
      if (internalTx != null) {
        internalTx.setValue(uncdBalance);
      }
      return true;
    } catch (ContractValidateException e) {
      logger.error("SVM Uncd: validate failure. Reason: {}", e.getMessage());
    }
    if (internalTx != null) {
      internalTx.reject();
    }
    return false;
  }

  public long cdExpireTime(DataWord targetAddress, DataWord resourceType) {
    byte[] owner = TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes());
    byte[] target = TransactionTrace.convertToStabilaAddress(targetAddress.getLast20Bytes());
    int resourceCode = resourceType.intValue();
    if (FastByteComparisons.isEqual(owner, target)) {
      AccountCapsule ownerCapsule = getContractState().getAccount(owner);
      if (resourceCode == 0) { //  for bandwidth
        if (ownerCapsule.getCdedCount() != 0
            && ownerCapsule.getCdedList().get(0).getCdedBalance() != 0) {
          return ownerCapsule.getCdedList().get(0).getExpireTime();
        }
      } else if (resourceCode == 1) { // for ucr
        if (ownerCapsule.getAccountResource().getCdedBalanceForUcr().getCdedBalance() != 0) {
          return ownerCapsule.getAccountResource().getCdedBalanceForUcr().getExpireTime();
        }
      }
    } else {
      byte[] key = DelegatedResourceCapsule.createDbKey(owner, target);
      DelegatedResourceCapsule delegatedResourceCapsule = getContractState().getDelegatedResource(key);
      if (delegatedResourceCapsule != null) {
        if (resourceCode == 0) {
          if (delegatedResourceCapsule.getCdedBalanceForBandwidth() != 0) {
            return delegatedResourceCapsule.getExpireTimeForBandwidth();
          }
        } else if (resourceCode == 1) {
          if (delegatedResourceCapsule.getCdedBalanceForUcr() != 0) {
            return delegatedResourceCapsule.getExpireTimeForUcr();
          }
        }
      }
    }
    return 0;
  }

  private Common.ResourceCode parseResourceCode(DataWord resourceType) {
    switch (resourceType.intValue()) {
      case 0:
        return Common.ResourceCode.BANDWIDTH;
      case 1:
        return Common.ResourceCode.UCR;
      default:
        return Common.ResourceCode.UNRECOGNIZED;
    }
  }

  private String convertResourceToString(DataWord resourceType) {
    switch (resourceType.intValue()) {
      case 0:
        return "Bandwidth";
      case 1:
        return "Ucr";
      default:
        return "UnknownType";
    }
  }

  public boolean voteExecutive(int executiveArrayOffset, int executiveArrayLength,
                             int amountArrayOffset, int amountArrayLength) {
    Repository repository = getContractState().newRepositoryChild();
    byte[] owner = TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes());

    increaseNonce();
    InternalTransaction internalTx = addInternalTx(null, owner, null, 0, null,
        "voteExecutive", nonce, null);

    if (memoryLoad(executiveArrayOffset).intValueSafe() != executiveArrayLength ||
        memoryLoad(amountArrayOffset).intValueSafe() != amountArrayLength) {
      logger.warn("SVM VoteExecutive: memory array length do not match length parameter!");
      throw new BytecodeExecutionException(
          "SVM VoteExecutive: memory array length do not match length parameter!");
    }

    if (executiveArrayLength != amountArrayLength) {
      logger.warn("SVM VoteExecutive: executive array length {} does not match amount array length {}",
          executiveArrayLength, amountArrayLength);
      return false;
    }

    try {
      VoteExecutiveParam param = new VoteExecutiveParam();
      param.setVoterAddress(owner);

      byte[] executiveArrayData = memoryChunk(Math.addExact(executiveArrayOffset, DataWord.WORD_SIZE),
          Math.multiplyExact(executiveArrayLength, DataWord.WORD_SIZE));
      byte[] amountArrayData = memoryChunk(Math.addExact(amountArrayOffset, DataWord.WORD_SIZE),
          Math.multiplyExact(amountArrayLength, DataWord.WORD_SIZE));

      for (int i = 0; i < executiveArrayLength; i++) {
        DataWord executive = new DataWord(Arrays.copyOfRange(executiveArrayData,
            i * DataWord.WORD_SIZE, (i + 1) * DataWord.WORD_SIZE));
        DataWord amount = new DataWord(Arrays.copyOfRange(amountArrayData,
            i * DataWord.WORD_SIZE, (i + 1) * DataWord.WORD_SIZE));
        param.addVote(TransactionTrace.convertToStabilaAddress(executive.getLast20Bytes()),
            amount.sValue().longValueExact());
      }
      if (internalTx != null) {
        internalTx.setExtra(param.toJsonStr());
      }

      VoteExecutiveProcessor processor = new VoteExecutiveProcessor();
      processor.validate(param, repository);
      processor.execute(param, repository);
      repository.commit();
      return true;
    } catch (ContractValidateException e) {
      logger.error("SVM VoteExecutive: validate failure. Reason: {}", e.getMessage());
    } catch (ContractExeException e) {
      logger.error("SVM VoteExecutive: execute failure. Reason: {}", e.getMessage());
    } catch (ArithmeticException e) {
      logger.error("SVM VoteExecutive: int or long out of range. caused by: {}", e.getMessage());
    }
    if (internalTx != null) {
      internalTx.reject();
    }
    return false;
  }

  public long withdrawReward() {
    Repository repository = getContractState().newRepositoryChild();
    byte[] owner = TransactionTrace.convertToStabilaAddress(getContractAddress().getLast20Bytes());

    increaseNonce();
    InternalTransaction internalTx = addInternalTx(null, owner, owner, 0, null,
        "withdrawReward", nonce, null);

    WithdrawRewardParam param = new WithdrawRewardParam();
    param.setOwnerAddress(owner);
    param.setNowInMs(getTimestamp().longValue() * 1000);
    try {
      WithdrawRewardProcessor processor = new WithdrawRewardProcessor();
      processor.validate(param, repository);
      long allowance = processor.execute(param, repository);
      repository.commit();
      if (internalTx != null) {
        internalTx.setValue(allowance);
      }
      return allowance;
    } catch (ContractValidateException e) {
      logger.error("SVM WithdrawReward: validate failure. Reason: {}", e.getMessage());
    } catch (ContractExeException e) {
      logger.error("SVM WithdrawReward: execute failure. Reason: {}", e.getMessage());
    }
    if (internalTx != null) {
      internalTx.reject();
    }
    return 0;
  }

  /**
   * Denotes problem when executing Ethereum bytecode. From blockchain and peer perspective this is
   * quite normal situation and doesn't mean exceptional situation in terms of the program
   * execution
   */
  @SuppressWarnings("serial")
  public static class BytecodeExecutionException extends RuntimeException {

    public BytecodeExecutionException(String message) {
      super(message);
    }

    public BytecodeExecutionException(String message, Object... args) {
      super(format(message, args));
    }
  }

  public static class AssetIssueException extends BytecodeExecutionException {

    public AssetIssueException(String message, Object... args) {
      super(format(message, args));
    }
  }

  public static class TransferException extends BytecodeExecutionException {

    public TransferException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class OutOfUcrException extends BytecodeExecutionException {

    public OutOfUcrException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class OutOfTimeException extends BytecodeExecutionException {

    public OutOfTimeException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class OutOfMemoryException extends BytecodeExecutionException {

    public OutOfMemoryException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class OutOfStorageException extends BytecodeExecutionException {

    public OutOfStorageException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class PrecompiledContractException extends BytecodeExecutionException {

    public PrecompiledContractException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class IllegalOperationException extends BytecodeExecutionException {

    public IllegalOperationException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class BadJumpDestinationException extends BytecodeExecutionException {

    public BadJumpDestinationException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class StackTooSmallException extends BytecodeExecutionException {

    public StackTooSmallException(String message, Object... args) {
      super(format(message, args));
    }
  }

  @SuppressWarnings("serial")
  public static class ReturnDataCopyIllegalBoundsException extends BytecodeExecutionException {

    public ReturnDataCopyIllegalBoundsException(DataWord off, DataWord size,
        long returnDataSize) {
      super(String
          .format(
              "Illegal RETURNDATACOPY arguments: offset (%s) + size (%s) > RETURNDATASIZE (%d)",
              off, size, returnDataSize));
    }
  }

  @SuppressWarnings("serial")
  public static class JVMStackOverFlowException extends BytecodeExecutionException {

    public JVMStackOverFlowException() {
      super("StackOverflowError:  exceed default JVM stack size!");
    }
  }

  @SuppressWarnings("serial")
  public static class StaticCallModificationException extends BytecodeExecutionException {

    public StaticCallModificationException() {
      super("Attempt to call a state modifying opcode inside STATICCALL");
    }
  }

  public static class Exception {

    private Exception() {
    }

    public static OutOfUcrException notEnoughOpUcr(OpCode op, long opUcr,
        long programUcr) {
      return new OutOfUcrException(
          "Not enough ucr for '%s' operation executing: opUcr[%d], programUcr[%d];", op,
          opUcr,
          programUcr);
    }

    public static OutOfUcrException notEnoughOpUcr(OpCode op, DataWord opUcr,
        DataWord programUcr) {
      return notEnoughOpUcr(op, opUcr.longValue(), programUcr.longValue());
    }

    public static OutOfUcrException notEnoughSpendUcr(String hint, long needUcr,
        long leftUcr) {
      return new OutOfUcrException(
          "Not enough ucr for '%s' executing: needUcr[%d], leftUcr[%d];", hint, needUcr,
          leftUcr);
    }

    public static OutOfTimeException notEnoughTime(String op) {
      return new OutOfTimeException(
          "CPU timeout for '%s' operation executing", op);
    }

    public static OutOfTimeException alreadyTimeOut() {
      return new OutOfTimeException("Already Time Out");
    }


    public static OutOfMemoryException memoryOverflow(OpCode op) {
      return new OutOfMemoryException("Out of Memory when '%s' operation executing", op.name());
    }

    public static OutOfStorageException notEnoughStorage() {
      return new OutOfStorageException("Not enough ContractState resource");
    }

    public static PrecompiledContractException contractValidateException(StabilaException e) {
      return new PrecompiledContractException(e.getMessage());
    }

    public static PrecompiledContractException contractExecuteException(StabilaException e) {
      return new PrecompiledContractException(e.getMessage());
    }

    public static OutOfUcrException ucrOverflow(BigInteger actualUcr,
        BigInteger ucrLimit) {
      return new OutOfUcrException("Ucr value overflow: actualUcr[%d], ucrLimit[%d];",
          actualUcr.longValueExact(), ucrLimit.longValueExact());
    }

    public static IllegalOperationException invalidOpCode(byte... opCode) {
      return new IllegalOperationException("Invalid operation code: opCode[%s];",
          Hex.toHexString(opCode, 0, 1));
    }

    public static BadJumpDestinationException badJumpDestination(int pc) {
      return new BadJumpDestinationException("Operation with pc isn't 'JUMPDEST': PC[%d];", pc);
    }

    public static StackTooSmallException tooSmallStack(int expectedSize, int actualSize) {
      return new StackTooSmallException("Expected stack size %d but actual %d;", expectedSize,
          actualSize);
    }
  }

  @SuppressWarnings("serial")
  public class StackTooLargeException extends BytecodeExecutionException {

    public StackTooLargeException(String message) {
      super(message);
    }
  }
}
