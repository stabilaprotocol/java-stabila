package org.stabila.core.services.http.solidity;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.common.application.Service;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.core.config.args.Args;
import org.stabila.core.services.http.FullNodeHttpApiService;
import org.stabila.core.services.http.GetAccountByIdServlet;
import org.stabila.core.services.http.GetAccountServlet;
import org.stabila.core.services.http.GetAssetIssueByIdServlet;
import org.stabila.core.services.http.GetAssetIssueByNameServlet;
import org.stabila.core.services.http.GetAssetIssueListByNameServlet;
import org.stabila.core.services.http.GetAssetIssueListServlet;
import org.stabila.core.services.http.GetBlockByIdServlet;
import org.stabila.core.services.http.GetBlockByLatestNumServlet;
import org.stabila.core.services.http.GetBlockByLimitNextServlet;
import org.stabila.core.services.http.GetBlockByNumServlet;
import org.stabila.core.services.http.GetBrokerageServlet;
import org.stabila.core.services.http.GetBurnStbServlet;
import org.stabila.core.services.http.GetDelegatedResourceAccountIndexServlet;
import org.stabila.core.services.http.GetDelegatedResourceServlet;
import org.stabila.core.services.http.GetExchangeByIdServlet;
import org.stabila.core.services.http.GetMarketOrderByAccountServlet;
import org.stabila.core.services.http.GetMarketOrderByIdServlet;
import org.stabila.core.services.http.GetMarketOrderListByPairServlet;
import org.stabila.core.services.http.GetMarketPairListServlet;
import org.stabila.core.services.http.GetMarketPriceByPairServlet;
import org.stabila.core.services.http.GetMerkleTreeVoucherInfoServlet;
import org.stabila.core.services.http.GetNodeInfoServlet;
import org.stabila.core.services.http.GetNowBlockServlet;
import org.stabila.core.services.http.GetPaginatedAssetIssueListServlet;
import org.stabila.core.services.http.GetRewardServlet;
import org.stabila.core.services.http.GetTransactionCountByBlockNumServlet;
import org.stabila.core.services.http.GetTransactionInfoByBlockNumServlet;
import org.stabila.core.services.http.IsShieldedTRC20ContractNoteSpentServlet;
import org.stabila.core.services.http.IsSpendServlet;
import org.stabila.core.services.http.ListExchangesServlet;
import org.stabila.core.services.http.ListExecutivesServlet;
import org.stabila.core.services.http.ScanAndMarkNoteByIvkServlet;
import org.stabila.core.services.http.ScanNoteByIvkServlet;
import org.stabila.core.services.http.ScanNoteByOvkServlet;
import org.stabila.core.services.http.ScanShieldedTRC20NotesByIvkServlet;
import org.stabila.core.services.http.ScanShieldedTRC20NotesByOvkServlet;
import org.stabila.core.services.http.TriggerConstantContractServlet;


@Component
@Slf4j(topic = "API")
public class SolidityNodeHttpApiService implements Service {

  private int port = Args.getInstance().getSolidityHttpPort();

  private Server server;

  @Autowired
  private GetAccountServlet getAccountServlet;

  @Autowired
  private GetTransactionByIdSolidityServlet getTransactionByIdServlet;
  @Autowired
  private GetTransactionInfoByIdSolidityServlet getTransactionInfoByIdServlet;
  @Autowired
  private GetTransactionCountByBlockNumServlet getTransactionCountByBlockNumServlet;
  @Autowired
  private GetDelegatedResourceServlet getDelegatedResourceServlet;
  @Autowired
  private GetDelegatedResourceAccountIndexServlet getDelegatedResourceAccountIndexServlet;
  @Autowired
  private GetExchangeByIdServlet getExchangeByIdServlet;
  @Autowired
  private ListExchangesServlet listExchangesServlet;

  @Autowired
  private ListExecutivesServlet listExecutivesServlet;
  @Autowired
  private GetAssetIssueListServlet getAssetIssueListServlet;
  @Autowired
  private GetPaginatedAssetIssueListServlet getPaginatedAssetIssueListServlet;
  @Autowired
  private GetAssetIssueByNameServlet getAssetIssueByNameServlet;
  @Autowired
  private GetAssetIssueByIdServlet getAssetIssueByIdServlet;
  @Autowired
  private GetAssetIssueListByNameServlet getAssetIssueListByNameServlet;
  @Autowired
  private GetNowBlockServlet getNowBlockServlet;
  @Autowired
  private GetBlockByNumServlet getBlockByNumServlet;
  @Autowired
  private GetNodeInfoServlet getNodeInfoServlet;
  @Autowired
  private GetAccountByIdServlet getAccountByIdServlet;
  @Autowired
  private GetBlockByIdServlet getBlockByIdServlet;
  @Autowired
  private GetBlockByLimitNextServlet getBlockByLimitNextServlet;
  @Autowired
  private GetBlockByLatestNumServlet getBlockByLatestNumServlet;
  @Autowired
  private ScanAndMarkNoteByIvkServlet scanAndMarkNoteByIvkServlet;
  @Autowired
  private ScanNoteByIvkServlet scanNoteByIvkServlet;
  @Autowired
  private ScanNoteByOvkServlet scanNoteByOvkServlet;
  @Autowired
  private GetMerkleTreeVoucherInfoServlet getMerkleTreeVoucherInfoServlet;
  @Autowired
  private IsSpendServlet isSpendServlet;
  @Autowired
  private ScanShieldedTRC20NotesByIvkServlet scanShieldedTRC20NotesByIvkServlet;
  @Autowired
  private ScanShieldedTRC20NotesByOvkServlet scanShieldedTRC20NotesByOvkServlet;
  @Autowired
  private IsShieldedTRC20ContractNoteSpentServlet isShieldedTRC20ContractNoteSpentServlet;
  @Autowired
  private GetMarketOrderByAccountServlet getMarketOrderByAccountServlet;
  @Autowired
  private GetMarketOrderByIdServlet getMarketOrderByIdServlet;
  @Autowired
  private GetMarketPriceByPairServlet getMarketPriceByPairServlet;
  @Autowired
  private GetMarketOrderListByPairServlet getMarketOrderListByPairServlet;
  @Autowired
  private GetMarketPairListServlet getMarketPairListServlet;
  @Autowired
  private GetBurnStbServlet getBurnStbServlet;
  @Autowired
  private GetBrokerageServlet getBrokerageServlet;
  @Autowired
  private GetRewardServlet getRewardServlet;
  @Autowired
  private TriggerConstantContractServlet triggerConstantContractServlet;

  @Autowired
  private GetTransactionInfoByBlockNumServlet getTransactionInfoByBlockNumServlet;

  @Override
  public void init() {
  }

  @Override
  public void init(CommonParameter args) {
    FullNodeHttpApiService.librustzcashInitZksnarkParams();
  }

  @Override
  public void start() {
    try {
      server = new Server(port);
      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      context.setContextPath("/");
      server.setHandler(context);

      // same as FullNode
      context.addServlet(new ServletHolder(getAccountServlet), "/walletsolidity/getaccount");
      context.addServlet(new ServletHolder(listExecutivesServlet), "/walletsolidity/listexecutives");
      context.addServlet(new ServletHolder(getAssetIssueListServlet),
          "/walletsolidity/getassetissuelist");
      context.addServlet(new ServletHolder(getPaginatedAssetIssueListServlet),
          "/walletsolidity/getpaginatedassetissuelist");
      context.addServlet(new ServletHolder(getAssetIssueByNameServlet),
          "/walletsolidity/getassetissuebyname");
      context.addServlet(new ServletHolder(getAssetIssueByIdServlet),
          "/walletsolidity/getassetissuebyid");
      context.addServlet(new ServletHolder(getAssetIssueListByNameServlet),
          "/walletsolidity/getassetissuelistbyname");
      context.addServlet(new ServletHolder(getNowBlockServlet), "/walletsolidity/getnowblock");
      context.addServlet(new ServletHolder(getBlockByNumServlet), "/walletsolidity/getblockbynum");
      context.addServlet(new ServletHolder(getDelegatedResourceServlet),
          "/walletsolidity/getdelegatedresource");
      context.addServlet(new ServletHolder(getDelegatedResourceAccountIndexServlet),
          "/walletsolidity/getdelegatedresourceaccountindex");
      context
          .addServlet(new ServletHolder(getExchangeByIdServlet),
              "/walletsolidity/getexchangebyid");
      context.addServlet(new ServletHolder(listExchangesServlet),
          "/walletsolidity/listexchanges");

      context.addServlet(new ServletHolder(getAccountByIdServlet),
          "/walletsolidity/getaccountbyid");
      context.addServlet(new ServletHolder(getBlockByIdServlet),
          "/walletsolidity/getblockbyid");
      context.addServlet(new ServletHolder(getBlockByLimitNextServlet),
          "/walletsolidity/getblockbylimitnext");
      context.addServlet(new ServletHolder(getBlockByLatestNumServlet),
          "/walletsolidity/getblockbylatestnum");

      // context.addServlet(new ServletHolder(getMerkleTreeVoucherInfoServlet),
      //     "/walletsolidity/getmerkletreevoucherinfo");
      // context.addServlet(new ServletHolder(scanAndMarkNoteByIvkServlet),
      //     "/walletsolidity/scanandmarknotebyivk");
      // context.addServlet(new ServletHolder(scanNoteByIvkServlet),
      //     "/walletsolidity/scannotebyivk");
      // context.addServlet(new ServletHolder(scanNoteByOvkServlet),
      //     "/walletsolidity/scannotebyovk");
      // context.addServlet(new ServletHolder(isSpendServlet),
      //     "/walletsolidity/isspend");

      context.addServlet(new ServletHolder(scanShieldedTRC20NotesByIvkServlet),
          "/walletsolidity/scanshieldedtrc20notesbyivk");
      context.addServlet(new ServletHolder(scanShieldedTRC20NotesByOvkServlet),
          "/walletsolidity/scanshieldedtrc20notesbyovk");
      context.addServlet(new ServletHolder(isShieldedTRC20ContractNoteSpentServlet),
          "/walletsolidity/isshieldedtrc20contractnotespent");

      context.addServlet(new ServletHolder(getTransactionInfoByBlockNumServlet),
          "/walletsolidity/gettransactioninfobyblocknum");

      context.addServlet(new ServletHolder(getMarketOrderByAccountServlet),
          "/walletsolidity/getmarketorderbyaccount");
      context.addServlet(new ServletHolder(getMarketOrderByIdServlet),
          "/walletsolidity/getmarketorderbyid");
      context.addServlet(new ServletHolder(getMarketPriceByPairServlet),
          "/walletsolidity/getmarketpricebypair");
      context.addServlet(new ServletHolder(getMarketOrderListByPairServlet),
          "/walletsolidity/getmarketorderlistbypair");
      context.addServlet(new ServletHolder(getMarketPairListServlet),
          "/walletsolidity/getmarketpairlist");

      // only for SolidityNode
      context.addServlet(new ServletHolder(getTransactionByIdServlet),
          "/walletsolidity/gettransactionbyid");

      context
          .addServlet(new ServletHolder(getTransactionInfoByIdServlet),
              "/walletsolidity/gettransactioninfobyid");
      context
          .addServlet(new ServletHolder(getTransactionCountByBlockNumServlet),
              "/walletsolidity/gettransactioncountbyblocknum");
      context.addServlet(new ServletHolder(triggerConstantContractServlet),
          "/walletsolidity/triggerconstantcontract");

      context.addServlet(new ServletHolder(getNodeInfoServlet), "/wallet/getnodeinfo");
      context.addServlet(new ServletHolder(getBrokerageServlet), "/walletsolidity/getBrokerage");
      context.addServlet(new ServletHolder(getRewardServlet), "/walletsolidity/getReward");
      context.addServlet(new ServletHolder(getBurnStbServlet), "/walletsolidity/getburnstb");

      int maxHttpConnectNumber = Args.getInstance().getMaxHttpConnectNumber();
      if (maxHttpConnectNumber > 0) {
        server.addBean(new ConnectionLimit(maxHttpConnectNumber, server));
      }

      server.start();
    } catch (Exception e) {
      logger.debug("IOException: {}", e.getMessage());
    }
  }

  @Override
  public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
    }
  }

}
