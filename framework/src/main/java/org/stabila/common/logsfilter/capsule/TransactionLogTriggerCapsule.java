package org.stabila.common.logsfilter.capsule;

import static org.stabila.protos.Protocol.Transaction.Contract.ContractType.TransferAssetContract;
import static org.stabila.protos.Protocol.Transaction.Contract.ContractType.TransferContract;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.stabila.common.logsfilter.EventPluginLoader;
import org.stabila.common.logsfilter.trigger.InternalTransactionPojo;
import org.stabila.common.logsfilter.trigger.TransactionLogTrigger;
import org.stabila.common.runtime.InternalTransaction;
import org.stabila.common.runtime.ProgramResult;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.db.TransactionTrace;
import org.stabila.protos.Protocol;
import org.stabila.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.stabila.protos.contract.BalanceContract.TransferContract;

@Slf4j
public class TransactionLogTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private TransactionLogTrigger transactionLogTrigger;

  public TransactionLogTriggerCapsule(TransactionCapsule stbCapsule, BlockCapsule blockCapsule) {
    transactionLogTrigger = new TransactionLogTrigger();
    if (Objects.nonNull(blockCapsule)) {
      transactionLogTrigger.setBlockHash(blockCapsule.getBlockId().toString());
    }
    transactionLogTrigger.setTransactionId(stbCapsule.getTransactionId().toString());
    transactionLogTrigger.setTimeStamp(blockCapsule.getTimeStamp());
    transactionLogTrigger.setBlockNumber(stbCapsule.getBlockNum());
    transactionLogTrigger.setData(Hex.toHexString(stbCapsule
        .getInstance().getRawData().getData().toByteArray()));

    TransactionTrace stbTrace = stbCapsule.getStbTrace();

    //result
    if (Objects.nonNull(stbCapsule.getContractRet())) {
      transactionLogTrigger.setResult(stbCapsule.getContractRet().toString());
    }

    if (Objects.nonNull(stbCapsule.getInstance().getRawData())) {
      // fee limit
      transactionLogTrigger.setFeeLimit(stbCapsule.getInstance().getRawData().getFeeLimit());

      Protocol.Transaction.Contract contract = stbCapsule.getInstance().getRawData().getContract(0);
      Any contractParameter = null;
      // contract type
      if (Objects.nonNull(contract)) {
        Protocol.Transaction.Contract.ContractType contractType = contract.getType();
        if (Objects.nonNull(contractType)) {
          transactionLogTrigger.setContractType(contractType.toString());
        }

        contractParameter = contract.getParameter();

        transactionLogTrigger.setContractCallValue(TransactionCapsule.getCallValue(contract));
      }

      if (Objects.nonNull(contractParameter) && Objects.nonNull(contract)) {
        try {
          if (contract.getType() == TransferContract) {
            TransferContract contractTransfer = contractParameter.unpack(TransferContract.class);

            if (Objects.nonNull(contractTransfer)) {
              transactionLogTrigger.setAssetName("stb");

              if (Objects.nonNull(contractTransfer.getOwnerAddress())) {
                transactionLogTrigger.setFromAddress(StringUtil
                    .encode58Check(contractTransfer.getOwnerAddress().toByteArray()));
              }

              if (Objects.nonNull(contractTransfer.getToAddress())) {
                transactionLogTrigger.setToAddress(
                    StringUtil.encode58Check(contractTransfer.getToAddress().toByteArray()));
              }

              transactionLogTrigger.setAssetAmount(contractTransfer.getAmount());
            }

          } else if (contract.getType() == TransferAssetContract) {
            TransferAssetContract contractTransfer = contractParameter
                .unpack(TransferAssetContract.class);

            if (Objects.nonNull(contractTransfer)) {
              if (Objects.nonNull(contractTransfer.getAssetName())) {
                transactionLogTrigger.setAssetName(contractTransfer.getAssetName().toStringUtf8());
              }

              if (Objects.nonNull(contractTransfer.getOwnerAddress())) {
                transactionLogTrigger.setFromAddress(
                    StringUtil.encode58Check(contractTransfer.getOwnerAddress().toByteArray()));
              }

              if (Objects.nonNull(contractTransfer.getToAddress())) {
                transactionLogTrigger.setToAddress(StringUtil
                    .encode58Check(contractTransfer.getToAddress().toByteArray()));
              }
              transactionLogTrigger.setAssetAmount(contractTransfer.getAmount());
            }
          }
        } catch (Exception e) {
          logger.error("failed to load transferAssetContract, error'{}'", e);
        }
      }
    }

    // receipt
    if (Objects.nonNull(stbTrace) && Objects.nonNull(stbTrace.getReceipt())) {
      transactionLogTrigger.setUcrFee(stbTrace.getReceipt().getUcrFee());
      transactionLogTrigger.setOriginUcrUsage(stbTrace.getReceipt().getOriginUcrUsage());
      transactionLogTrigger.setUcrUsageTotal(stbTrace.getReceipt().getUcrUsageTotal());
      transactionLogTrigger.setNetUsage(stbTrace.getReceipt().getNetUsage());
      transactionLogTrigger.setNetFee(stbTrace.getReceipt().getNetFee());
      transactionLogTrigger.setUcrUsage(stbTrace.getReceipt().getUcrUsage());
    }

    // program result
    if (Objects.nonNull(stbTrace) && Objects.nonNull(stbTrace.getRuntime()) && Objects
        .nonNull(stbTrace.getRuntime().getResult())) {
      ProgramResult programResult = stbTrace.getRuntime().getResult();
      ByteString contractResult = ByteString.copyFrom(programResult.getHReturn());
      ByteString contractAddress = ByteString.copyFrom(programResult.getContractAddress());

      if (Objects.nonNull(contractResult) && contractResult.size() > 0) {
        transactionLogTrigger.setContractResult(Hex.toHexString(contractResult.toByteArray()));
      }

      if (Objects.nonNull(contractAddress) && contractAddress.size() > 0) {
        transactionLogTrigger
            .setContractAddress(StringUtil.encode58Check((contractAddress.toByteArray())));
      }

      // internal transaction
      transactionLogTrigger.setInternalTransactionList(
          getInternalTransactionList(programResult.getInternalTransactions()));
    }
  }

  public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
    transactionLogTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
  }

  private List<InternalTransactionPojo> getInternalTransactionList(
      List<InternalTransaction> internalTransactionList) {
    List<InternalTransactionPojo> pojoList = new ArrayList<>();

    internalTransactionList.forEach(internalTransaction -> {
      InternalTransactionPojo item = new InternalTransactionPojo();

      item.setHash(Hex.toHexString(internalTransaction.getHash()));
      item.setCallValue(internalTransaction.getValue());
      item.setTokenInfo(internalTransaction.getTokenInfo());
      item.setCaller_address(Hex.toHexString(internalTransaction.getSender()));
      item.setTransferTo_address(Hex.toHexString(internalTransaction.getTransferToAddress()));
      item.setData(Hex.toHexString(internalTransaction.getData()));
      item.setRejected(internalTransaction.isRejected());
      item.setNote(internalTransaction.getNote());
      item.setExtra(internalTransaction.getExtra());

      pojoList.add(item);
    });

    return pojoList;
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postTransactionTrigger(transactionLogTrigger);
  }
}
