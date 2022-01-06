package org.stabila.core.consensus;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.stabila.core.capsule.ProposalCapsule;
import org.stabila.core.config.Parameter.ForkBlockVersionEnum;
import org.stabila.core.db.Manager;
import org.stabila.core.utils.ProposalUtil;

/**
 * Notice:
 * <p>
 * if you want to add a proposal,you just should add a enum ProposalType and add the valid in the
 * validator method, add the process in the process method
 */
@Slf4j
public class ProposalService extends ProposalUtil {

  public static boolean process(Manager manager, ProposalCapsule proposalCapsule) {
    Map<Long, Long> map = proposalCapsule.getInstance().getParametersMap();
    boolean find = true;
    for (Map.Entry<Long, Long> entry : map.entrySet()) {
      ProposalType proposalType = ProposalType.getEnumOrNull(entry.getKey());
      if (proposalType == null) {
        find = false;
        continue;
      }
      switch (proposalType) {
        case MAINTENANCE_TIME_INTERVAL: {
          manager.getDynamicPropertiesStore().saveMaintenanceTimeInterval(entry.getValue());
          break;
        }
        case ACCOUNT_UPGRADE_COST: {
          manager.getDynamicPropertiesStore().saveAccountUpgradeCost(entry.getValue());
          break;
        }
        case CREATE_ACCOUNT_FEE: {
          manager.getDynamicPropertiesStore().saveCreateAccountFee(entry.getValue());
          break;
        }
        case TRANSACTION_FEE: {
          manager.getDynamicPropertiesStore().saveTransactionFee(entry.getValue());
          break;
        }
        case ASSET_ISSUE_FEE: {
          manager.getDynamicPropertiesStore().saveAssetIssueFee(entry.getValue());
          break;
        }
        case EXECUTIVE_PAY_PER_BLOCK: {
          manager.getDynamicPropertiesStore().saveExecutivePayPerBlock(entry.getValue());
          break;
        }
        case EXECUTIVE_STANDBY_ALLOWANCE: {
          manager.getDynamicPropertiesStore().saveExecutiveStandbyAllowance(entry.getValue());
          break;
        }
        case CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT: {
          manager.getDynamicPropertiesStore()
              .saveCreateNewAccountFeeInSystemContract(entry.getValue());
          break;
        }
        case CREATE_NEW_ACCOUNT_BANDWIDTH_RATE: {
          manager.getDynamicPropertiesStore().saveCreateNewAccountBandwidthRate(entry.getValue());
          break;
        }
        case ALLOW_CREATION_OF_CONTRACTS: {
          manager.getDynamicPropertiesStore().saveAllowCreationOfContracts(entry.getValue());
          break;
        }
        case REMOVE_THE_POWER_OF_THE_GR: {
          if (manager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr() == 0) {
            manager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(entry.getValue());
          }
          break;
        }
        case UCR_FEE: {
          manager.getDynamicPropertiesStore().saveUcrFee(entry.getValue());
          break;
        }
        case EXCHANGE_CREATE_FEE: {
          manager.getDynamicPropertiesStore().saveExchangeCreateFee(entry.getValue());
          break;
        }
        case MAX_CPU_TIME_OF_ONE_TX: {
          manager.getDynamicPropertiesStore().saveMaxCpuTimeOfOneTx(entry.getValue());
          break;
        }
        case ALLOW_UPDATE_ACCOUNT_NAME: {
          manager.getDynamicPropertiesStore().saveAllowUpdateAccountName(entry.getValue());
          break;
        }
        case ALLOW_SAME_TOKEN_NAME: {
          manager.getDynamicPropertiesStore().saveAllowSameTokenName(entry.getValue());
          break;
        }
        case ALLOW_DELEGATE_RESOURCE: {
          manager.getDynamicPropertiesStore().saveAllowDelegateResource(entry.getValue());
          break;
        }
        case TOTAL_UCR_LIMIT: {
          manager.getDynamicPropertiesStore().saveTotalUcrLimit(entry.getValue());
          break;
        }
        case ALLOW_SVM_TRANSFER_SRC10: {
          manager.getDynamicPropertiesStore().saveAllowSvmTransferSrc10(entry.getValue());
          break;
        }
        case TOTAL_CURRENT_UCR_LIMIT: {
          manager.getDynamicPropertiesStore().saveTotalUcrLimit2(entry.getValue());
          break;
        }
        case ALLOW_MULTI_SIGN: {
          if (manager.getDynamicPropertiesStore().getAllowMultiSign() == 0) {
            manager.getDynamicPropertiesStore().saveAllowMultiSign(entry.getValue());
          }
          break;
        }
        case ALLOW_ADAPTIVE_UCR: {
          if (manager.getDynamicPropertiesStore().getAllowAdaptiveUcr() == 0) {
            manager.getDynamicPropertiesStore().saveAllowAdaptiveUcr(entry.getValue());
            if (manager.getChainBaseManager()
                .getForkController().pass(ForkBlockVersionEnum.VERSION_3_6_5)) {
              //24 * 60 * 2 . one minute,1/2 total limit.
              manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitTargetRatio(2880);
              manager.getDynamicPropertiesStore().saveTotalUcrTargetLimit(
                  manager.getDynamicPropertiesStore().getTotalUcrLimit() / 2880);
              manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitMultiplier(50);
            }
          }
          break;
        }
        case UPDATE_ACCOUNT_PERMISSION_FEE: {
          manager.getDynamicPropertiesStore().saveUpdateAccountPermissionFee(entry.getValue());
          break;
        }
        case MULTI_SIGN_FEE: {
          manager.getDynamicPropertiesStore().saveMultiSignFee(entry.getValue());
          break;
        }
        case ALLOW_PROTO_FILTER_NUM: {
          manager.getDynamicPropertiesStore().saveAllowProtoFilterNum(entry.getValue());
          break;
        }
        case ALLOW_ACCOUNT_STATE_ROOT: {
          manager.getDynamicPropertiesStore().saveAllowAccountStateRoot(entry.getValue());
          break;
        }
        case ALLOW_SVM_CONSTANTINOPLE: {
          manager.getDynamicPropertiesStore().saveAllowSvmConstantinople(entry.getValue());
          manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(48);
          break;
        }
        case ALLOW_SVM_SOLIDITY_059: {
          manager.getDynamicPropertiesStore().saveAllowSvmSolidity059(entry.getValue());
          break;
        }
        case ADAPTIVE_RESOURCE_LIMIT_TARGET_RATIO: {
          long ratio = 24 * 60 * entry.getValue();
          manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitTargetRatio(ratio);
          manager.getDynamicPropertiesStore().saveTotalUcrTargetLimit(
              manager.getDynamicPropertiesStore().getTotalUcrLimit() / ratio);
          break;
        }
        case ADAPTIVE_RESOURCE_LIMIT_MULTIPLIER: {
          manager.getDynamicPropertiesStore().saveAdaptiveResourceLimitMultiplier(entry.getValue());
          break;
        }
        case ALLOW_CHANGE_DELEGATION: {
          manager.getDynamicPropertiesStore().saveChangeDelegation(entry.getValue());
          manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(49);
          break;
        }
        case EXECUTIVE_100_PAY_PER_BLOCK: {
          manager.getDynamicPropertiesStore().saveExecutive100PayPerBlock(entry.getValue());
          break;
        }
        //case ALLOW_SHIELDED_TRANSACTION: {
        //  if (manager.getDynamicPropertiesStore().getAllowShieldedTransaction() == 0) {
        //    manager.getDynamicPropertiesStore().saveAllowShieldedTransaction(entry.getValue());
        //    manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(51);
        //  }
        //  break;
        //}
        //case SHIELDED_TRANSACTION_FEE: {
        //  manager.getDynamicPropertiesStore().saveShieldedTransactionFee(entry.getValue());
        //  break;
        //}
        //        case SHIELDED_TRANSACTION_CREATE_ACCOUNT_FEE: {
        //          manager.getDynamicPropertiesStore()
        //              .saveShieldedTransactionCreateAccountFee(entry.getValue());
        //          break;
        //        }
        case FORBID_TRANSFER_TO_CONTRACT: {
          manager.getDynamicPropertiesStore().saveForbidTransferToContract(entry.getValue());
          break;
        }
        case ALLOW_PBFT: {
          manager.getDynamicPropertiesStore().saveAllowPBFT(entry.getValue());
          break;
        }
        case ALLOW_SVM_ISTANBUL: {
          manager.getDynamicPropertiesStore().saveAllowSvmIstanbul(entry.getValue());
          break;
        }
        case ALLOW_SHIELDED_SRC20_TRANSACTION: {
          manager.getDynamicPropertiesStore().saveAllowShieldedSRC20Transaction(entry.getValue());
          break;
        }
        case ALLOW_MARKET_TRANSACTION: {
          if (manager.getDynamicPropertiesStore().getAllowMarketTransaction() == 0) {
            manager.getDynamicPropertiesStore().saveAllowMarketTransaction(entry.getValue());
            manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(52);
            manager.getDynamicPropertiesStore().addSystemContractAndSetPermission(53);
          }
          break;
        }
        case MARKET_SELL_FEE: {
          manager.getDynamicPropertiesStore().saveMarketSellFee(entry.getValue());
          break;
        }
        case MARKET_CANCEL_FEE: {
          manager.getDynamicPropertiesStore().saveMarketCancelFee(entry.getValue());
          break;
        }
        case MAX_FEE_LIMIT: {
          manager.getDynamicPropertiesStore().saveMaxFeeLimit(entry.getValue());
          break;
        }
        case ALLOW_TRANSACTION_FEE_POOL: {
          manager.getDynamicPropertiesStore().saveAllowTransactionFeePool(entry.getValue());
          break;
        }
        case ALLOW_BLACKHOLE_OPTIMIZATION: {
          manager.getDynamicPropertiesStore().saveAllowBlackHoleOptimization(entry.getValue());
          break;
        }
        case ALLOW_NEW_RESOURCE_MODEL: {
          manager.getDynamicPropertiesStore().saveAllowNewResourceModel(entry.getValue());
          break;
        }
        case ALLOW_SVM_CD: {
          manager.getDynamicPropertiesStore().saveAllowSvmCd(entry.getValue());
          break;
        }
        case ALLOW_SVM_VOTE: {
          manager.getDynamicPropertiesStore().saveAllowSvmVote(entry.getValue());
          manager.getDynamicPropertiesStore().saveNewRewardAlgorithmEffectiveCycle();
          break;
        }
        case FREE_NET_LIMIT: {
          manager.getDynamicPropertiesStore().saveFreeNetLimit(entry.getValue());
          break;
        }
        case TOTAL_NET_LIMIT: {
          manager.getDynamicPropertiesStore().saveTotalNetLimit(entry.getValue());
          break;
        }
        case ALLOW_ACCOUNT_ASSET_OPTIMIZATION: {
          manager.getDynamicPropertiesStore().setAllowAccountAssetOptimization(entry.getValue());
          break;
        }
        case DEPLOY_CONTRACT_FEE: {
          manager.getDynamicPropertiesStore().saveDeployContractFee(entry.getValue());
          break;
        }
        default:
          find = false;
          break;
      }
    }
    return find;
  }

}
