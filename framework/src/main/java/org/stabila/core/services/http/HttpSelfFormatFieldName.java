package org.stabila.core.services.http;

import java.util.HashMap;
import java.util.Map;

public class HttpSelfFormatFieldName {

  private static Map<String, Integer> AddressFieldNameMap = new HashMap<>();
  private static Map<String, Integer> NameFieldNameMap = new HashMap<>();

  static {
    //***** api.proto *****
    //DelegatedResourceMessage
    AddressFieldNameMap.put("protocol.DelegatedResourceMessage.fromAddress", 1);
    AddressFieldNameMap.put("protocol.DelegatedResourceMessage.toAddress", 1);
    //EasyTransferMessage
    AddressFieldNameMap.put("protocol.EasyTransferMessage.toAddress", 1);
    //EasyTransferAssetMessage
    AddressFieldNameMap.put("protocol.EasyTransferAssetMessage.toAddress", 1);
    //EasyTransferByPrivateMessage
    AddressFieldNameMap.put("protocol.EasyTransferByPrivateMessage.toAddress", 1);
    //EasyTransferAssetByPrivateMessage
    AddressFieldNameMap.put("protocol.EasyTransferAssetByPrivateMessage.toAddress", 1);
    //TransactionSignWeight
    AddressFieldNameMap.put("protocol.TransactionSignWeight.approved_list", 1);
    //TransactionApprovedList
    AddressFieldNameMap.put("protocol.TransactionApprovedList.approved_list", 1);
    //PrivateParameters
    AddressFieldNameMap.put("protocol.PrivateParameters.transparent_from_address", 1);
    AddressFieldNameMap.put("protocol.PrivateParameters.transparent_to_address", 1);
    //PrivateParametersWithoutAsk
    AddressFieldNameMap.put("protocol.PrivateParametersWithoutAsk.transparent_from_address", 1);
    AddressFieldNameMap.put("protocol.PrivateParametersWithoutAsk.transparent_to_address", 1);
    //PrivateShieldedSRC20Parameters
    AddressFieldNameMap.put(
        "protocol.PrivateShieldedSRC20Parameters.transparent_to_address", 1);
    AddressFieldNameMap.put(
        "protocol.PrivateShieldedSRC20Parameters.shielded_SRC20_contract_address", 1);
    //PrivateShieldedSRC20ParametersWithoutAsk
    AddressFieldNameMap.put(
        "protocol.PrivateShieldedSRC20ParametersWithoutAsk.transparent_to_address", 1);
    AddressFieldNameMap.put(
        "protocol.PrivateShieldedSRC20ParametersWithoutAsk.shielded_SRC20_contract_address", 1);
    //IvkDecryptSRC20Parameters
    AddressFieldNameMap.put(
        "protocol.IvkDecryptSRC20Parameters.shielded_SRC20_contract_address", 1);
    //OvkDecryptSRC20Parameters
    AddressFieldNameMap.put(
        "protocol.OvkDecryptSRC20Parameters.shielded_SRC20_contract_address", 1);
    //NfSRC20Parameters
    AddressFieldNameMap.put(
        "protocol.NfSRC20Parameters.shielded_SRC20_contract_address", 1);
    //ShieldedSRC20TriggerContractParameters
    AddressFieldNameMap.put(
        "protocol.ShieldedSRC20TriggerContractParameters.transparent_to_address", 1);
    AddressFieldNameMap.put(
        "protocol.DecryptNotesSRC20.NoteTx.transparent_to_address", 1);

    //***** Contract.proto *****
    //AccountCreateContract
    AddressFieldNameMap.put("protocol.AccountCreateContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.AccountCreateContract.account_address", 1);
    //AccountUpdateContract
    AddressFieldNameMap.put("protocol.AccountUpdateContract.owner_address", 1);
    //SetAccountIdContract
    AddressFieldNameMap.put("protocol.SetAccountIdContract.owner_address", 1);
    //TransferContract
    AddressFieldNameMap.put("protocol.TransferContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.TransferContract.to_address", 1);
    //CancelDeferredTransactionContract
    AddressFieldNameMap.put("protocol.CancelDeferredTransactionContract.ownerAddress", 1);
    //TransferAssetContract
    AddressFieldNameMap.put("protocol.TransferAssetContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.TransferAssetContract.to_address", 1);
    //VoteAssetContract
    AddressFieldNameMap.put("protocol.VoteAssetContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.VoteAssetContract.vote_address", 1);
    //VoteExecutiveContract
    AddressFieldNameMap.put("protocol.VoteExecutiveContract.Vote.vote_address", 1);
    AddressFieldNameMap.put("protocol.VoteExecutiveContract.owner_address", 1);
    //UpdateSettingContract
    AddressFieldNameMap.put("protocol.UpdateSettingContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.UpdateSettingContract.contract_address", 1);
    //UpdateUcrLimitContract
    AddressFieldNameMap.put("protocol.UpdateUcrLimitContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.UpdateUcrLimitContract.contract_address", 1);
    //ClearABIContract
    AddressFieldNameMap.put("protocol.ClearABIContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.ClearABIContract.contract_address", 1);
    //ExecutiveCreateContract
    AddressFieldNameMap.put("protocol.ExecutiveCreateContract.owner_address", 1);
    //ExecutiveUpdateContract
    AddressFieldNameMap.put("protocol.ExecutiveUpdateContract.owner_address", 1);
    //AssetIssueContract
    AddressFieldNameMap.put("protocol.AssetIssueContract.owner_address", 1);
    //ParticipateAssetIssueContract
    AddressFieldNameMap.put("protocol.ParticipateAssetIssueContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.ParticipateAssetIssueContract.to_address", 1);
    //CdBalanceContract
    AddressFieldNameMap.put("protocol.CdBalanceContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.CdBalanceContract.receiver_address", 1);
    //UncdBalanceContract
    AddressFieldNameMap.put("protocol.UncdBalanceContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.UncdBalanceContract.receiver_address", 1);
    //UncdAssetContract
    AddressFieldNameMap.put("protocol.UncdAssetContract.owner_address", 1);
    //WithdrawBalanceContract
    AddressFieldNameMap.put("protocol.WithdrawBalanceContract.owner_address", 1);
    //UpdateAssetContract
    AddressFieldNameMap.put("protocol.UpdateAssetContract.owner_address", 1);
    //ProposalCreateContract
    AddressFieldNameMap.put("protocol.ProposalCreateContract.owner_address", 1);
    //ProposalApproveContract
    AddressFieldNameMap.put("protocol.ProposalApproveContract.owner_address", 1);
    //ProposalDeleteContract
    AddressFieldNameMap.put("protocol.ProposalDeleteContract.owner_address", 1);
    //CreateSmartContract
    AddressFieldNameMap.put("protocol.CreateSmartContract.owner_address", 1);
    //TriggerSmartContract
    AddressFieldNameMap.put("protocol.TriggerSmartContract.owner_address", 1);
    AddressFieldNameMap.put("protocol.TriggerSmartContract.contract_address", 1);
    //BuyStorageContract
    AddressFieldNameMap.put("protocol.BuyStorageContract.owner_address", 1);
    //BuyStorageBytesContract
    AddressFieldNameMap.put("protocol.BuyStorageBytesContract.owner_address", 1);
    //SellStorageContract
    AddressFieldNameMap.put("protocol.SellStorageContract.owner_address", 1);
    //ExchangeCreateContract
    AddressFieldNameMap.put("protocol.ExchangeCreateContract.owner_address", 1);
    //ExchangeInjectContract
    AddressFieldNameMap.put("protocol.ExchangeInjectContract.owner_address", 1);
    //ExchangeWithdrawContract
    AddressFieldNameMap.put("protocol.ExchangeWithdrawContract.owner_address", 1);
    //ExchangeTransactionContract
    AddressFieldNameMap.put("protocol.ExchangeTransactionContract.owner_address", 1);
    //AccountPermissionUpdateContract
    AddressFieldNameMap.put("protocol.AccountPermissionUpdateContract.owner_address", 1);
    //UpdateBrokerageContract
    AddressFieldNameMap.put("protocol.UpdateBrokerageContract.owner_address", 1);
    //ShieldedTransferContract
    AddressFieldNameMap.put("protocol.ShieldedTransferContract.transparent_from_address", 1);
    AddressFieldNameMap.put("protocol.ShieldedTransferContract.transparent_to_address", 1);
    //UpdateBrokerageContract
    AddressFieldNameMap.put("protocol.UpdateBrokerageContract.owner_address", 1);

    //***** Stabila.proto *****
    //AccountId
    AddressFieldNameMap.put("protocol.AccountId.address", 1);
    //Vote
    AddressFieldNameMap.put("protocol.Vote.vote_address", 1);
    //Proposal
    AddressFieldNameMap.put("protocol.Proposal.proposer_address", 1);
    AddressFieldNameMap.put("protocol.Proposal.approvals", 1);
    //Exchange
    AddressFieldNameMap.put("protocol.Exchange.creator_address", 1);
    //Account
    AddressFieldNameMap.put("protocol.Account.address", 1);
    //Key
    AddressFieldNameMap.put("protocol.Key.address", 1);
    //DelegatedResource
    AddressFieldNameMap.put("protocol.DelegatedResource.from", 1);
    AddressFieldNameMap.put("protocol.DelegatedResource.to", 1);
    //Executive
    AddressFieldNameMap.put("protocol.Executive.address", 1);
    //Votes
    AddressFieldNameMap.put("protocol.Votes.address", 1);
    //TransactionInfo
    AddressFieldNameMap.put("protocol.TransactionInfo.Log.address", 1);
    AddressFieldNameMap.put("protocol.TransactionInfo.contract_address", 1);
    //DeferredTransaction
    AddressFieldNameMap.put("protocol.DeferredTransaction.senderAddress", 1);
    AddressFieldNameMap.put("protocol.DeferredTransaction.receiverAddress", 1);
    //BlockHeader
    AddressFieldNameMap.put("protocol.BlockHeader.raw.executive_address", 1);
    //SmartContract
    AddressFieldNameMap.put("protocol.SmartContract.origin_address", 1);
    AddressFieldNameMap.put("protocol.SmartContract.contract_address", 1);
    //InternalTransaction
    AddressFieldNameMap.put("protocol.InternalTransaction.caller_address", 1);
    AddressFieldNameMap.put("protocol.InternalTransaction.transferTo_address", 1);
    //DelegatedResourceAccountIndex
    AddressFieldNameMap.put("protocol.DelegatedResourceAccountIndex.account", 1);
    AddressFieldNameMap.put("protocol.DelegatedResourceAccountIndex.fromAccounts", 1);
    AddressFieldNameMap.put("protocol.DelegatedResourceAccountIndex.toAccounts", 1);

    AddressFieldNameMap.put("protocol.AccountIdentifier.address", 1);
    AddressFieldNameMap.put("protocol.TransactionBalanceTrace.Operation.address", 1);

    //***** api.proto *****
    //Return
    NameFieldNameMap.put("protocol.Return.message", 1);
    //Address
    NameFieldNameMap.put("protocol.Address.host", 1);
    //EasyTransferMessage
    NameFieldNameMap.put("protocol.EasyTransferMessage.passPhrase", 1);
    //EasyTransferAssetMessage
    NameFieldNameMap.put("protocol.EasyTransferAssetMessage.passPhrase", 1);
    //Note
    NameFieldNameMap.put("protocol.Note.memo", 1);

    //***** Contract.proto *****
    //AccountUpdateContract
    NameFieldNameMap.put("protocol.AccountUpdateContract.account_name", 1);
    //SetAccountIdContract
    NameFieldNameMap.put("protocol.SetAccountIdContract.account_id", 1);
    //TransferAssetContract
    NameFieldNameMap.put("protocol.TransferAssetContract.asset_name", 1);
    //ExecutiveCreateContract
    NameFieldNameMap.put("protocol.ExecutiveCreateContract.url", 1);
    //ExecutiveUpdateContract
    NameFieldNameMap.put("protocol.ExecutiveUpdateContract.update_url", 1);
    //AssetIssueContract
    NameFieldNameMap.put("protocol.AssetIssueContract.name", 1);
    NameFieldNameMap.put("protocol.AssetIssueContract.abbr", 1);
    NameFieldNameMap.put("protocol.AssetIssueContract.description", 1);
    NameFieldNameMap.put("protocol.AssetIssueContract.url", 1);
    //ParticipateAssetIssueContract
    NameFieldNameMap.put("protocol.ParticipateAssetIssueContract.asset_name", 1);
    //UpdateAssetContract
    NameFieldNameMap.put("protocol.UpdateAssetContract.description", 1);
    NameFieldNameMap.put("protocol.UpdateAssetContract.url", 1);
    //ExchangeCreateContract
    NameFieldNameMap.put("protocol.ExchangeCreateContract.first_token_id", 1);
    NameFieldNameMap.put("protocol.ExchangeCreateContract.second_token_id", 1);
    //ExchangeInjectContract
    NameFieldNameMap.put("protocol.ExchangeInjectContract.token_id", 1);
    //ExchangeWithdrawContract
    NameFieldNameMap.put("protocol.ExchangeWithdrawContract.token_id", 1);
    //ExchangeTransactionContract
    NameFieldNameMap.put("protocol.ExchangeTransactionContract.token_id", 1);

    //***** Stabila.proto *****
    //AccountId
    NameFieldNameMap.put("protocol.AccountId.name", 1);
    //Exchange
    NameFieldNameMap.put("protocol.Exchange.first_token_id", 1);
    NameFieldNameMap.put("protocol.Exchange.second_token_id", 1);
    //Account
    NameFieldNameMap.put("protocol.Account.account_name", 1);
    NameFieldNameMap.put("protocol.Account.asset_issued_name", 1);
    NameFieldNameMap.put("protocol.Account.asset_issued_ID", 1);
    NameFieldNameMap.put("protocol.Account.account_id", 1);
    //authority
    NameFieldNameMap.put("protocol.authority.permission_name", 1);
    //Transaction
    NameFieldNameMap.put("protocol.Transaction.Contract.ContractName", 1);
    //TransactionInfo
    NameFieldNameMap.put("protocol.TransactionInfo.resMessage", 1);

    //***** market.proto *****
    // MarketSellAssetContract
    AddressFieldNameMap.put("protocol.MarketSellAssetContract.owner_address", 1);
    NameFieldNameMap.put("protocol.MarketSellAssetContract.sell_token_id", 1);
    NameFieldNameMap.put("protocol.MarketSellAssetContract.buy_token_id", 1);

    // MarketCancelOrderContract
    AddressFieldNameMap.put("protocol.MarketCancelOrderContract.owner_address", 1);

    // MarketOrder
    AddressFieldNameMap.put("protocol.MarketOrder.owner_address", 1);
    NameFieldNameMap.put("protocol.MarketOrder.sell_token_id", 1);
    NameFieldNameMap.put("protocol.MarketOrder.buy_token_id", 1);

    // MarketOrderPair
    NameFieldNameMap.put("protocol.MarketOrderPair.sell_token_id", 1);
    NameFieldNameMap.put("protocol.MarketOrderPair.buy_token_id", 1);

    // MarketPriceList
    NameFieldNameMap.put("protocol.MarketPriceList.sell_token_id", 1);
    NameFieldNameMap.put("protocol.MarketPriceList.buy_token_id", 1);
  }

  public static boolean isAddressFormat(final String name) {
    return AddressFieldNameMap.containsKey(name);
  }

  public static boolean isNameStringFormat(final String name) {
    return NameFieldNameMap.containsKey(name);
  }
}
