/*
 * java-stabila is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-stabila is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.stabila.core.capsule.utils;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.ReceiptCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.capsule.TransactionInfoCapsule;
import org.stabila.core.db.TransactionTrace;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.runtime.InternalTransaction;
import org.stabila.common.runtime.ProgramResult;
import org.stabila.common.runtime.vm.LogInfo;
import org.stabila.common.utils.DecodeUtil;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract;
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.Protocol.TransactionInfo.Log;
import org.stabila.protos.Protocol.TransactionInfo.code;
import org.stabila.protos.contract.BalanceContract.TransferContract;

@Slf4j(topic = "capsule")
public class TransactionUtil {

  public static Transaction newGenesisTransaction(byte[] key, long value)
      throws IllegalArgumentException {

    if (!DecodeUtil.addressValid(key)) {
      throw new IllegalArgumentException("Invalid address");
    }
    TransferContract transferContract = TransferContract.newBuilder()
        .setAmount(value)
        .setOwnerAddress(ByteString.copyFrom("0x000000000000000000000".getBytes()))
        .setToAddress(ByteString.copyFrom(key))
        .build();

    return new TransactionCapsule(transferContract,
        Contract.ContractType.TransferContract).getInstance();
  }

  public static TransactionInfoCapsule buildTransactionInfoInstance(TransactionCapsule stbCap,
                                                                    BlockCapsule block, TransactionTrace trace) {

    TransactionInfo.Builder builder = TransactionInfo.newBuilder();
    ReceiptCapsule traceReceipt = trace.getReceipt();
    builder.setResult(code.SUCESS);
    if (StringUtils.isNoneEmpty(trace.getRuntimeError()) || Objects
        .nonNull(trace.getRuntimeResult().getException())) {
      builder.setResult(code.FAILED);
      builder.setResMessage(ByteString.copyFromUtf8(trace.getRuntimeError()));
    }
    builder.setId(ByteString.copyFrom(stbCap.getTransactionId().getBytes()));
    ProgramResult programResult = trace.getRuntimeResult();
    long fee =
        programResult.getRet().getFee() + traceReceipt.getEnergyFee()
            + traceReceipt.getNetFee() + traceReceipt.getMultiSignFee();

    boolean supportTransactionFeePool = trace.getTransactionContext().getStoreFactory()
        .getChainBaseManager().getDynamicPropertiesStore().supportTransactionFeePool();
    if (supportTransactionFeePool) {
      long packingFee = 0L;
      if (trace.isNetFeeForBandwidth()) {
        packingFee += traceReceipt.getNetFee();
      }
      if (!traceReceipt.getResult().equals(Transaction.Result.contractResult.OUT_OF_TIME)) {
        packingFee += traceReceipt.getEnergyFee();
      }
      builder.setPackingFee(packingFee);
    }

    ByteString contractResult = ByteString.copyFrom(programResult.getHReturn());
    ByteString ContractAddress = ByteString.copyFrom(programResult.getContractAddress());

    builder.setFee(fee);
    builder.addContractResult(contractResult);
    builder.setContractAddress(ContractAddress);
    builder.setUnfreezeAmount(programResult.getRet().getUnfreezeAmount());
    builder.setAssetIssueID(programResult.getRet().getAssetIssueID());
    builder.setExchangeId(programResult.getRet().getExchangeId());
    builder.setWithdrawAmount(programResult.getRet().getWithdrawAmount());
    builder.setExchangeReceivedAmount(programResult.getRet().getExchangeReceivedAmount());
    builder.setExchangeInjectAnotherAmount(programResult.getRet().getExchangeInjectAnotherAmount());
    builder.setExchangeWithdrawAnotherAmount(
        programResult.getRet().getExchangeWithdrawAnotherAmount());
    builder.setShieldedTransactionFee(programResult.getRet().getShieldedTransactionFee());
    builder.setOrderId(programResult.getRet().getOrderId());
    builder.addAllOrderDetails(programResult.getRet().getOrderDetailsList());

    List<Log> logList = new ArrayList<>();
    programResult.getLogInfoList().forEach(
        logInfo -> {
          logList.add(LogInfo.buildLog(logInfo));
        }
    );
    builder.addAllLog(logList);

    if (Objects.nonNull(block)) {
      builder.setBlockNumber(block.getInstance().getBlockHeader().getRawData().getNumber());
      builder.setBlockTimeStamp(block.getInstance().getBlockHeader().getRawData().getTimestamp());
    }

    builder.setReceipt(traceReceipt.getReceipt());

    if (CommonParameter.getInstance().isSaveInternalTx() && null != programResult
        .getInternalTransactions()) {
      for (InternalTransaction internalTransaction : programResult
          .getInternalTransactions()) {
        Protocol.InternalTransaction.Builder internalStbBuilder = Protocol.InternalTransaction
            .newBuilder();
        // set hash
        internalStbBuilder.setHash(ByteString.copyFrom(internalTransaction.getHash()));
        // set caller
        internalStbBuilder.setCallerAddress(ByteString.copyFrom(internalTransaction.getSender()));
        // set TransferTo
        internalStbBuilder
            .setTransferToAddress(ByteString.copyFrom(internalTransaction.getTransferToAddress()));
        //TODO: "for loop" below in future for multiple token case, we only have one for now.
        Protocol.InternalTransaction.CallValueInfo.Builder callValueInfoBuilder =
            Protocol.InternalTransaction.CallValueInfo.newBuilder();
        // stb will not be set token name
        callValueInfoBuilder.setCallValue(internalTransaction.getValue());
        // Just one transferBuilder for now.
        internalStbBuilder.addCallValueInfo(callValueInfoBuilder);
        internalTransaction.getTokenInfo().forEach((tokenId, amount) -> {
          internalStbBuilder.addCallValueInfo(
              Protocol.InternalTransaction.CallValueInfo.newBuilder().setTokenId(tokenId)
                  .setCallValue(amount));
        });
        // Token for loop end here
        internalStbBuilder.setNote(ByteString.copyFrom(internalTransaction.getNote().getBytes()));
        internalStbBuilder.setRejected(internalTransaction.isRejected());
        internalStbBuilder.setExtra(internalTransaction.getExtra());
        builder.addInternalTransactions(internalStbBuilder);
      }
    }

    return new TransactionInfoCapsule(builder.build());
  }

  public static boolean isNumber(byte[] id) {
    if (ArrayUtils.isEmpty(id)) {
      return false;
    }
    for (byte b : id) {
      if (b < '0' || b > '9') {
        return false;
      }
    }

    return !(id.length > 1 && id[0] == '0');
  }
}
