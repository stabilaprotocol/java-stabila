package org.stabila.common.logsfilter.capsule;

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
import org.stabila.protos.contract.AccountContract.*;
import org.stabila.protos.contract.AssetIssueContractOuterClass.*;
import org.stabila.protos.contract.BalanceContract.*;
import org.stabila.protos.contract.ExchangeContract.*;
import org.stabila.protos.contract.ExecutiveContract.*;
import org.stabila.protos.contract.MarketContract.*;
import org.stabila.protos.contract.ProposalContract.*;
import org.stabila.protos.contract.ShieldContract.*;
import org.stabila.protos.contract.SmartContractOuterClass.*;
import org.stabila.protos.contract.StorageContract.*;
import org.stabila.protos.contract.VoteAssetContractOuterClass.*;

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
          switch (contract.getType()) {
            case TransferContract:
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
              break;
            case TransferAssetContract:
              TransferAssetContract transferAssetContract = contractParameter
                      .unpack(TransferAssetContract.class);

              if (Objects.nonNull(transferAssetContract)) {
                if (Objects.nonNull(transferAssetContract.getAssetName())) {
                  transactionLogTrigger.setAssetName(transferAssetContract.getAssetName().toStringUtf8());
                }

                if (Objects.nonNull(transferAssetContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(transferAssetContract.getOwnerAddress().toByteArray()));
                }

                if (Objects.nonNull(transferAssetContract.getToAddress())) {
                  transactionLogTrigger.setToAddress(StringUtil
                          .encode58Check(transferAssetContract.getToAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(transferAssetContract.getAmount());
              }
              break;
            case AccountCreateContract:
              AccountCreateContract accountCreateContract = contractParameter
                      .unpack(AccountCreateContract.class);
              if (Objects.nonNull(accountCreateContract)) {
                if (Objects.nonNull(accountCreateContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(accountCreateContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(accountCreateContract.getAccountAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(accountCreateContract.getAccountAddress().toByteArray()));
                }
              }
              break;
            case VoteAssetContract:
              VoteAssetContract voteAssetContract = contractParameter
                      .unpack(VoteAssetContract.class);
              if (Objects.nonNull(voteAssetContract)) {
                if (Objects.nonNull(voteAssetContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(voteAssetContract.getOwnerAddress().toByteArray()));
                }
                if (voteAssetContract.getVoteAddressCount() > 0 && Objects.nonNull(voteAssetContract.getVoteAddress(0))) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(voteAssetContract.getVoteAddress(0).toByteArray()));
                }
                if (voteAssetContract.getCount() > 0) {
                  transactionLogTrigger.setAssetAmount(voteAssetContract.getCount());
                }
              }
              break;
            case VoteExecutiveContract:
              VoteExecutiveContract voteExecutiveContract = contractParameter
                      .unpack(VoteExecutiveContract.class);
              if (Objects.nonNull(voteExecutiveContract)) {
                if (Objects.nonNull(voteExecutiveContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(voteExecutiveContract.getOwnerAddress().toByteArray()));
                }
                if (voteExecutiveContract.getVotesCount() > 0) {
                  if (Objects.nonNull(voteExecutiveContract.getVotes(0).getVoteAddress())) {
                    transactionLogTrigger.setToAddress(
                            StringUtil.encode58Check(voteExecutiveContract.getVotes(0).getVoteAddress().toByteArray()));
                  }
                  transactionLogTrigger.setAssetAmount(voteExecutiveContract.getVotes(0).getVoteCount());
                }
              }
              break;
            case ExecutiveCreateContract:
              ExecutiveCreateContract executiveCreateContract = contractParameter
                      .unpack(ExecutiveCreateContract.class);
              if (Objects.nonNull(executiveCreateContract)) {
                if (Objects.nonNull(executiveCreateContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(executiveCreateContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(executiveCreateContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case AssetIssueContract:
              AssetIssueContract assetIssueContract = contractParameter
                      .unpack(AssetIssueContract.class);
              if (Objects.nonNull(assetIssueContract)) {
                if (Objects.nonNull(assetIssueContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(assetIssueContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(assetIssueContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(assetIssueContract.getTotalSupply());
              }
              break;
            case ExecutiveUpdateContract:
              ExecutiveUpdateContract executiveUpdateContract = contractParameter
                      .unpack(ExecutiveUpdateContract.class);
              if (Objects.nonNull(executiveUpdateContract)) {
                if (Objects.nonNull(executiveUpdateContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(executiveUpdateContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(executiveUpdateContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case ParticipateAssetIssueContract:
              ParticipateAssetIssueContract participateAssetIssueContract = contractParameter
                      .unpack(ParticipateAssetIssueContract.class);
              if (Objects.nonNull(participateAssetIssueContract)) {
                if (Objects.nonNull(participateAssetIssueContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(participateAssetIssueContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(participateAssetIssueContract.getToAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(participateAssetIssueContract.getToAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(participateAssetIssueContract.getAmount());
              }
              break;
            case AccountUpdateContract:
              AccountUpdateContract accountUpdateContract = contractParameter
                      .unpack(AccountUpdateContract.class);
              if (Objects.nonNull(accountUpdateContract)) {
                if (Objects.nonNull(accountUpdateContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(accountUpdateContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(accountUpdateContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case CdBalanceContract:
              CdBalanceContract cdBalanceContract = contractParameter
                      .unpack(CdBalanceContract.class);
              if (Objects.nonNull(cdBalanceContract)) {
                if (Objects.nonNull(cdBalanceContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(cdBalanceContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(cdBalanceContract.getReceiverAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(cdBalanceContract.getReceiverAddress().toByteArray()));
                } else if (Objects.nonNull(cdBalanceContract.getOwnerAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(cdBalanceContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(cdBalanceContract.getCdedBalance());
              }
              break;
            case UncdBalanceContract:
              UncdBalanceContract uncdBalanceContract = contractParameter
                      .unpack(UncdBalanceContract.class);
              if (Objects.nonNull(uncdBalanceContract)) {
                if (Objects.nonNull(uncdBalanceContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(uncdBalanceContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(uncdBalanceContract.getReceiverAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(uncdBalanceContract.getReceiverAddress().toByteArray()));
                } else if (Objects.nonNull(uncdBalanceContract.getOwnerAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(uncdBalanceContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case WithdrawBalanceContract:
              WithdrawBalanceContract withdrawBalanceContract = contractParameter
                      .unpack(WithdrawBalanceContract.class);
              if (Objects.nonNull(withdrawBalanceContract)) {
                if (Objects.nonNull(withdrawBalanceContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(withdrawBalanceContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(withdrawBalanceContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case UncdAssetContract:
              UncdAssetContract uncdAssetContract = contractParameter
                      .unpack(UncdAssetContract.class);
              if (Objects.nonNull(uncdAssetContract)) {
                if (Objects.nonNull(uncdAssetContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(uncdAssetContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(uncdAssetContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case UpdateAssetContract:
              UpdateAssetContract updateAssetContract = contractParameter
                      .unpack(UpdateAssetContract.class);
              if (Objects.nonNull(updateAssetContract)) {
                if (Objects.nonNull(updateAssetContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(updateAssetContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(updateAssetContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case ProposalCreateContract:
              ProposalCreateContract proposalCreateContract = contractParameter
                      .unpack(ProposalCreateContract.class);
              if (Objects.nonNull(proposalCreateContract)) {
                if (Objects.nonNull(proposalCreateContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(proposalCreateContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(proposalCreateContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(proposalCreateContract.getParametersCount());
              }
              break;
            case ProposalApproveContract:
              ProposalApproveContract proposalApproveContract = contractParameter
                      .unpack(ProposalApproveContract.class);
              if (Objects.nonNull(proposalApproveContract)) {
                if (Objects.nonNull(proposalApproveContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(proposalApproveContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(proposalApproveContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(proposalApproveContract.getProposalId());
              }
              break;
            case ProposalDeleteContract:
              ProposalDeleteContract proposalDeleteContract = contractParameter
                      .unpack(ProposalDeleteContract.class);
              if (Objects.nonNull(proposalDeleteContract)) {
                if (Objects.nonNull(proposalDeleteContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(proposalDeleteContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(proposalDeleteContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(proposalDeleteContract.getProposalId());
              }
              break;
            case SetAccountIdContract:
              SetAccountIdContract setAccountIdContract = contractParameter
                      .unpack(SetAccountIdContract.class);
              if (Objects.nonNull(setAccountIdContract)) {
                if (Objects.nonNull(setAccountIdContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(setAccountIdContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(setAccountIdContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case CreateSmartContract:
              CreateSmartContract createSmartContract = contractParameter
                      .unpack(CreateSmartContract.class);
              if (Objects.nonNull(createSmartContract)) {
                if (Objects.nonNull(createSmartContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(createSmartContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(createSmartContract.getNewContract()) &&
                        Objects.nonNull(createSmartContract.getNewContract().getContractAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(createSmartContract.getNewContract().getContractAddress().toByteArray()));
                } else if (Objects.nonNull(createSmartContract.getOwnerAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(createSmartContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case TriggerSmartContract:
              TriggerSmartContract triggerSmartContract = contractParameter
                      .unpack(TriggerSmartContract.class);
              if (Objects.nonNull(triggerSmartContract)) {
                if (Objects.nonNull(triggerSmartContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(triggerSmartContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(triggerSmartContract.getContractAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(triggerSmartContract.getContractAddress().toByteArray()));
                }
              }
              break;
            case UpdateSettingContract:
              UpdateSettingContract updateSettingContract = contractParameter
                      .unpack(UpdateSettingContract.class);
              if (Objects.nonNull(updateSettingContract)) {
                if (Objects.nonNull(updateSettingContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(updateSettingContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(updateSettingContract.getContractAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(updateSettingContract.getContractAddress().toByteArray()));
                }
              }
              break;
            case ExchangeCreateContract:
              ExchangeCreateContract exchangeCreateContract = contractParameter
                      .unpack(ExchangeCreateContract.class);
              if (Objects.nonNull(exchangeCreateContract)) {
                if (Objects.nonNull(exchangeCreateContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(exchangeCreateContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(exchangeCreateContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(exchangeCreateContract.getFirstTokenBalance());
              }
              break;
            case ExchangeInjectContract:
              ExchangeInjectContract exchangeInjectContract = contractParameter
                      .unpack(ExchangeInjectContract.class);
              if (Objects.nonNull(exchangeInjectContract)) {
                if (Objects.nonNull(exchangeInjectContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(exchangeInjectContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(exchangeInjectContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(exchangeInjectContract.getQuant());
              }
              break;
            case ExchangeWithdrawContract:
              ExchangeWithdrawContract exchangeWithdrawContract = contractParameter
                      .unpack(ExchangeWithdrawContract.class);
              if (Objects.nonNull(exchangeWithdrawContract)) {
                if (Objects.nonNull(exchangeWithdrawContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(exchangeWithdrawContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(exchangeWithdrawContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(exchangeWithdrawContract.getQuant());
              }
              break;
            case ExchangeTransactionContract:
              ExchangeTransactionContract exchangeTransactionContract = contractParameter
                      .unpack(ExchangeTransactionContract.class);
              if (Objects.nonNull(exchangeTransactionContract)) {
                if (Objects.nonNull(exchangeTransactionContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(exchangeTransactionContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(exchangeTransactionContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(exchangeTransactionContract.getQuant());
              }
              break;
            case UpdateUcrLimitContract:
              UpdateUcrLimitContract updateUcrLimitContract = contractParameter
                      .unpack(UpdateUcrLimitContract.class);
              if (Objects.nonNull(updateUcrLimitContract)) {
                if (Objects.nonNull(updateUcrLimitContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(updateUcrLimitContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(updateUcrLimitContract.getContractAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(updateUcrLimitContract.getContractAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(updateUcrLimitContract.getOriginUcrLimit());
              }
              break;
            case AccountPermissionUpdateContract:
              AccountPermissionUpdateContract accountPermissionUpdateContract = contractParameter
                      .unpack(AccountPermissionUpdateContract.class);
              if (Objects.nonNull(accountPermissionUpdateContract)) {
                if (Objects.nonNull(accountPermissionUpdateContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(accountPermissionUpdateContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(accountPermissionUpdateContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            case ClearABIContract:
              ClearABIContract clearABIContract = contractParameter
                      .unpack(ClearABIContract.class);
              if (Objects.nonNull(clearABIContract)) {
                if (Objects.nonNull(clearABIContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(clearABIContract.getOwnerAddress().toByteArray()));
                }
                if (Objects.nonNull(clearABIContract.getContractAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(clearABIContract.getContractAddress().toByteArray()));
                }
              }
              break;
            case UpdateBrokerageContract:
              UpdateBrokerageContract updateBrokerageContract = contractParameter
                      .unpack(UpdateBrokerageContract.class);
              if (Objects.nonNull(updateBrokerageContract)) {
                if (Objects.nonNull(updateBrokerageContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(updateBrokerageContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(updateBrokerageContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(updateBrokerageContract.getBrokerage());
              }
              break;
            case ShieldedTransferContract:
              ShieldedTransferContract shieldedTransferContract = contractParameter
                      .unpack(ShieldedTransferContract.class);
              if (Objects.nonNull(shieldedTransferContract)) {
                if (Objects.nonNull(shieldedTransferContract.getTransparentFromAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(shieldedTransferContract.getTransparentFromAddress().toByteArray()));
                }
                if (Objects.nonNull(shieldedTransferContract.getTransparentToAddress())) {
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(shieldedTransferContract.getTransparentToAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(shieldedTransferContract.getFromAmount());
              }
              break;
            case MarketSellAssetContract:
              MarketSellAssetContract marketSellAssetContract = contractParameter
                      .unpack(MarketSellAssetContract.class);
              if (Objects.nonNull(marketSellAssetContract)) {
                if (Objects.nonNull(marketSellAssetContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(marketSellAssetContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(marketSellAssetContract.getOwnerAddress().toByteArray()));
                }
                transactionLogTrigger.setAssetAmount(marketSellAssetContract.getSellTokenQuantity());
              }
              break;
            case MarketCancelOrderContract:
              MarketCancelOrderContract marketCancelOrderContract = contractParameter
                      .unpack(MarketCancelOrderContract.class);
              if (Objects.nonNull(marketCancelOrderContract)) {
                if (Objects.nonNull(marketCancelOrderContract.getOwnerAddress())) {
                  transactionLogTrigger.setFromAddress(
                          StringUtil.encode58Check(marketCancelOrderContract.getOwnerAddress().toByteArray()));
                  transactionLogTrigger.setToAddress(
                          StringUtil.encode58Check(marketCancelOrderContract.getOwnerAddress().toByteArray()));
                }
              }
              break;
            default:

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
