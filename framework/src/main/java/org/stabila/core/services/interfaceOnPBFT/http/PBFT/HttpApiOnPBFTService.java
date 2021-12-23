package org.stabila.core.services.interfaceOnPBFT.http.PBFT;

import java.util.EnumSet;
import javax.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.stabila.common.application.Service;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.core.config.args.Args;
import org.stabila.core.services.filter.LiteFnQueryHttpFilter;
import org.stabila.core.services.interfaceOnPBFT.http.GetAccountByIdOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetAccountOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetAssetIssueByIdOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetAssetIssueByNameOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetAssetIssueListByNameOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetAssetIssueListOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetBlockByIdOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetBlockByLatestNumOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetBlockByLimitNextOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetBlockByNumOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetBrokerageOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetBurnStbOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetDelegatedResourceAccountIndexOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetDelegatedResourceOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetExchangeByIdOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetMarketOrderByAccountOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetMarketOrderByIdOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetMarketOrderListByPairOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetMarketPairListOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetMarketPriceByPairOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetMerkleTreeVoucherInfoOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetNodeInfoOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetNowBlockOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetPaginatedAssetIssueListOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetRewardOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.GetTransactionCountByBlockNumOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.IsShieldedSRC20ContractNoteSpentOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.IsSpendOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.ListExchangesOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.ListExecutivesOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.ScanAndMarkNoteByIvkOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.ScanNoteByIvkOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.ScanNoteByOvkOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.ScanShieldedSRC20NotesByIvkOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.ScanShieldedSRC20NotesByOvkOnPBFTServlet;
import org.stabila.core.services.interfaceOnPBFT.http.TriggerConstantContractOnPBFTServlet;

@Slf4j(topic = "API")
public class HttpApiOnPBFTService implements Service {

  private int port = Args.getInstance().getPBFTHttpPort();

  private Server server;

  @Autowired
  private GetAccountOnPBFTServlet accountOnPBFTServlet;

  @Autowired
  private GetTransactionByIdOnPBFTServlet getTransactionByIdOnPBFTServlet;
  @Autowired
  private GetTransactionInfoByIdOnPBFTServlet getTransactionInfoByIdOnPBFTServlet;
  @Autowired
  private ListExecutivesOnPBFTServlet listExecutivesOnPBFTServlet;
  @Autowired
  private GetAssetIssueListOnPBFTServlet getAssetIssueListOnPBFTServlet;
  @Autowired
  private GetPaginatedAssetIssueListOnPBFTServlet getPaginatedAssetIssueListOnPBFTServlet;
  @Autowired
  private GetNowBlockOnPBFTServlet getNowBlockOnPBFTServlet;
  @Autowired
  private GetBlockByNumOnPBFTServlet getBlockByNumOnPBFTServlet;

  @Autowired
  private GetNodeInfoOnPBFTServlet getNodeInfoOnPBFTServlet;

  @Autowired
  private GetDelegatedResourceOnPBFTServlet getDelegatedResourceOnPBFTServlet;
  @Autowired
  private GetDelegatedResourceAccountIndexOnPBFTServlet
      getDelegatedResourceAccountIndexOnPBFTServlet;
  @Autowired
  private GetExchangeByIdOnPBFTServlet getExchangeByIdOnPBFTServlet;
  @Autowired
  private ListExchangesOnPBFTServlet listExchangesOnPBFTServlet;
  @Autowired
  private GetTransactionCountByBlockNumOnPBFTServlet
      getTransactionCountByBlockNumOnPBFTServlet;
  @Autowired
  private GetAssetIssueByNameOnPBFTServlet getAssetIssueByNameOnPBFTServlet;
  @Autowired
  private GetAssetIssueByIdOnPBFTServlet getAssetIssueByIdOnPBFTServlet;
  @Autowired
  private GetAssetIssueListByNameOnPBFTServlet getAssetIssueListByNameOnPBFTServlet;
  @Autowired
  private GetAccountByIdOnPBFTServlet getAccountByIdOnPBFTServlet;
  @Autowired
  private GetBlockByIdOnPBFTServlet getBlockByIdOnPBFTServlet;
  @Autowired
  private GetBlockByLimitNextOnPBFTServlet getBlockByLimitNextOnPBFTServlet;
  @Autowired
  private GetBlockByLatestNumOnPBFTServlet getBlockByLatestNumOnPBFTServlet;
  @Autowired
  private GetMerkleTreeVoucherInfoOnPBFTServlet getMerkleTreeVoucherInfoOnPBFTServlet;
  @Autowired
  private ScanNoteByIvkOnPBFTServlet scanNoteByIvkOnPBFTServlet;
  @Autowired
  private ScanAndMarkNoteByIvkOnPBFTServlet scanAndMarkNoteByIvkOnPBFTServlet;
  @Autowired
  private ScanNoteByOvkOnPBFTServlet scanNoteByOvkOnPBFTServlet;
  @Autowired
  private IsSpendOnPBFTServlet isSpendOnPBFTServlet;
  @Autowired
  private GetBrokerageOnPBFTServlet getBrokerageServlet;
  @Autowired
  private GetRewardOnPBFTServlet getRewardServlet;
  @Autowired
  private TriggerConstantContractOnPBFTServlet triggerConstantContractOnPBFTServlet;

  @Autowired
  private LiteFnQueryHttpFilter liteFnQueryHttpFilter;

  @Autowired
  private GetMarketOrderByAccountOnPBFTServlet getMarketOrderByAccountOnPBFTServlet;
  @Autowired
  private GetMarketOrderByIdOnPBFTServlet getMarketOrderByIdOnPBFTServlet;
  @Autowired
  private GetMarketPriceByPairOnPBFTServlet getMarketPriceByPairOnPBFTServlet;
  @Autowired
  private GetMarketOrderListByPairOnPBFTServlet getMarketOrderListByPairOnPBFTServlet;
  @Autowired
  private GetMarketPairListOnPBFTServlet getMarketPairListOnPBFTServlet;

  @Autowired
  private ScanShieldedSRC20NotesByIvkOnPBFTServlet scanShieldedSRC20NotesByIvkOnPBFTServlet;
  @Autowired
  private ScanShieldedSRC20NotesByOvkOnPBFTServlet scanShieldedSRC20NotesByOvkOnPBFTServlet;
  @Autowired
  private IsShieldedSRC20ContractNoteSpentOnPBFTServlet
      isShieldedSRC20ContractNoteSpentOnPBFTServlet;
  @Autowired
  private GetBurnStbOnPBFTServlet getBurnStbOnPBFTServlet;

  @Override
  public void init() {

  }

  @Override
  public void init(CommonParameter parameter) {

  }

  @Override
  public void start() {
    try {
      server = new Server(port);
      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      context.setContextPath("/walletpbft/");
      server.setHandler(context);

      // same as FullNode
      context.addServlet(new ServletHolder(accountOnPBFTServlet), "/getaccount");
      context.addServlet(new ServletHolder(listExecutivesOnPBFTServlet), "/listexecutives");
      context.addServlet(new ServletHolder(getAssetIssueListOnPBFTServlet), "/getassetissuelist");
      context.addServlet(new ServletHolder(getPaginatedAssetIssueListOnPBFTServlet),
          "/getpaginatedassetissuelist");
      context
          .addServlet(new ServletHolder(getAssetIssueByNameOnPBFTServlet), "/getassetissuebyname");
      context.addServlet(new ServletHolder(getAssetIssueByIdOnPBFTServlet), "/getassetissuebyid");
      context.addServlet(new ServletHolder(getAssetIssueListByNameOnPBFTServlet),
          "/getassetissuelistbyname");
      context.addServlet(new ServletHolder(getNowBlockOnPBFTServlet), "/getnowblock");
      context.addServlet(new ServletHolder(getBlockByNumOnPBFTServlet), "/getblockbynum");
      context.addServlet(new ServletHolder(getDelegatedResourceOnPBFTServlet),
          "/getdelegatedresource");
      context.addServlet(new ServletHolder(getDelegatedResourceAccountIndexOnPBFTServlet),
          "/getdelegatedresourceaccountindex");
      context.addServlet(new ServletHolder(getExchangeByIdOnPBFTServlet), "/getexchangebyid");
      context.addServlet(new ServletHolder(listExchangesOnPBFTServlet), "/listexchanges");
      context.addServlet(new ServletHolder(getAccountByIdOnPBFTServlet), "/getaccountbyid");
      context.addServlet(new ServletHolder(getBlockByIdOnPBFTServlet), "/getblockbyid");
      context
          .addServlet(new ServletHolder(getBlockByLimitNextOnPBFTServlet), "/getblockbylimitnext");
      context
          .addServlet(new ServletHolder(getBlockByLatestNumOnPBFTServlet), "/getblockbylatestnum");
      context.addServlet(new ServletHolder(getMerkleTreeVoucherInfoOnPBFTServlet),
          "/getmerkletreevoucherinfo");
      context.addServlet(new ServletHolder(scanAndMarkNoteByIvkOnPBFTServlet),
          "/scanandmarknotebyivk");
      context.addServlet(new ServletHolder(scanNoteByIvkOnPBFTServlet), "/scannotebyivk");
      context.addServlet(new ServletHolder(scanNoteByOvkOnPBFTServlet), "/scannotebyovk");
      context.addServlet(new ServletHolder(isSpendOnPBFTServlet), "/isspend");
      context.addServlet(new ServletHolder(triggerConstantContractOnPBFTServlet),
          "/triggerconstantcontract");

      // only for PBFTNode
      context.addServlet(new ServletHolder(getTransactionByIdOnPBFTServlet), "/gettransactionbyid");
      context.addServlet(new ServletHolder(getTransactionInfoByIdOnPBFTServlet),
          "/gettransactioninfobyid");

      context.addServlet(new ServletHolder(getTransactionCountByBlockNumOnPBFTServlet),
          "/gettransactioncountbyblocknum");

      context.addServlet(new ServletHolder(getNodeInfoOnPBFTServlet), "/getnodeinfo");
      context.addServlet(new ServletHolder(getBrokerageServlet), "/getBrokerage");
      context.addServlet(new ServletHolder(getRewardServlet), "/getReward");

      context.addServlet(new ServletHolder(getMarketOrderByAccountOnPBFTServlet),
          "/getmarketorderbyaccount");
      context.addServlet(new ServletHolder(getMarketOrderByIdOnPBFTServlet),
          "/getmarketorderbyid");
      context.addServlet(new ServletHolder(getMarketPriceByPairOnPBFTServlet),
          "/getmarketpricebypair");
      context.addServlet(new ServletHolder(getMarketOrderListByPairOnPBFTServlet),
          "/getmarketorderlistbypair");
      context.addServlet(new ServletHolder(getMarketPairListOnPBFTServlet),
          "/getmarketpairlist");

      context.addServlet(new ServletHolder(scanShieldedSRC20NotesByIvkOnPBFTServlet),
          "/scanshieldedsrc20notesbyivk");
      context.addServlet(new ServletHolder(scanShieldedSRC20NotesByOvkOnPBFTServlet),
          "/scanshieldedsrc20notesbyovk");
      context.addServlet(new ServletHolder(isShieldedSRC20ContractNoteSpentOnPBFTServlet),
          "/isshieldedsrc20contractnotespent");
      context.addServlet(new ServletHolder(getBurnStbOnPBFTServlet),
          "/getburnstb");

      int maxHttpConnectNumber = Args.getInstance().getMaxHttpConnectNumber();
      if (maxHttpConnectNumber > 0) {
        server.addBean(new ConnectionLimit(maxHttpConnectNumber, server));
      }

      // filters the specified APIs
      // when node is lite fullnode and openHistoryQueryWhenLiteFN is false
      context.addFilter(new FilterHolder(liteFnQueryHttpFilter), "/*",
          EnumSet.allOf(DispatcherType.class));

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
