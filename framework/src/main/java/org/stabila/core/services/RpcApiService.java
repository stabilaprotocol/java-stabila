package org.stabila.core.services;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolStringList;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.api.DatabaseGrpc.DatabaseImplBase;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.AccountNetMessage;
import org.stabila.api.GrpcAPI.AccountResourceMessage;
import org.stabila.api.GrpcAPI.Address;
import org.stabila.api.GrpcAPI.AddressPrKeyPairMessage;
import org.stabila.api.GrpcAPI.AssetIssueList;
import org.stabila.api.GrpcAPI.BlockExtention;
import org.stabila.api.GrpcAPI.BlockLimit;
import org.stabila.api.GrpcAPI.BlockList;
import org.stabila.api.GrpcAPI.BlockListExtention;
import org.stabila.api.GrpcAPI.BlockReference;
import org.stabila.api.GrpcAPI.BytesMessage;
import org.stabila.api.GrpcAPI.DecryptNotes;
import org.stabila.api.GrpcAPI.DecryptNotesMarked;
import org.stabila.api.GrpcAPI.DecryptNotesTRC20;
import org.stabila.api.GrpcAPI.DelegatedResourceList;
import org.stabila.api.GrpcAPI.DelegatedResourceMessage;
import org.stabila.api.GrpcAPI.DiversifierMessage;
import org.stabila.api.GrpcAPI.EasyTransferAssetByPrivateMessage;
import org.stabila.api.GrpcAPI.EasyTransferAssetMessage;
import org.stabila.api.GrpcAPI.EasyTransferByPrivateMessage;
import org.stabila.api.GrpcAPI.EasyTransferMessage;
import org.stabila.api.GrpcAPI.EasyTransferResponse;
import org.stabila.api.GrpcAPI.EmptyMessage;
import org.stabila.api.GrpcAPI.ExchangeList;
import org.stabila.api.GrpcAPI.ExpandedSpendingKeyMessage;
import org.stabila.api.GrpcAPI.IncomingViewingKeyDiversifierMessage;
import org.stabila.api.GrpcAPI.IncomingViewingKeyMessage;
import org.stabila.api.GrpcAPI.IvkDecryptTRC20Parameters;
import org.stabila.api.GrpcAPI.NfTRC20Parameters;
import org.stabila.api.GrpcAPI.Node;
import org.stabila.api.GrpcAPI.NodeList;
import org.stabila.api.GrpcAPI.NoteParameters;
import org.stabila.api.GrpcAPI.NumberMessage;
import org.stabila.api.GrpcAPI.OvkDecryptTRC20Parameters;
import org.stabila.api.GrpcAPI.PaginatedMessage;
import org.stabila.api.GrpcAPI.PaymentAddressMessage;
import org.stabila.api.GrpcAPI.PrivateParameters;
import org.stabila.api.GrpcAPI.PrivateParametersWithoutAsk;
import org.stabila.api.GrpcAPI.PrivateShieldedTRC20Parameters;
import org.stabila.api.GrpcAPI.PrivateShieldedTRC20ParametersWithoutAsk;
import org.stabila.api.GrpcAPI.ProposalList;
import org.stabila.api.GrpcAPI.Return;
import org.stabila.api.GrpcAPI.Return.response_code;
import org.stabila.api.GrpcAPI.ShieldedAddressInfo;
import org.stabila.api.GrpcAPI.ShieldedTRC20Parameters;
import org.stabila.api.GrpcAPI.ShieldedTRC20TriggerContractParameters;
import org.stabila.api.GrpcAPI.SpendAuthSigParameters;
import org.stabila.api.GrpcAPI.SpendResult;
import org.stabila.api.GrpcAPI.TransactionApprovedList;
import org.stabila.api.GrpcAPI.TransactionExtention;
import org.stabila.api.GrpcAPI.TransactionIdList;
import org.stabila.api.GrpcAPI.TransactionInfoList;
import org.stabila.api.GrpcAPI.TransactionList;
import org.stabila.api.GrpcAPI.TransactionListExtention;
import org.stabila.api.GrpcAPI.TransactionSignWeight;
import org.stabila.api.GrpcAPI.ViewingKeyMessage;
import org.stabila.api.GrpcAPI.WitnessList;
import org.stabila.api.MonitorGrpc;
import org.stabila.api.WalletExtensionGrpc;
import org.stabila.api.WalletGrpc.WalletImplBase;
import org.stabila.api.WalletSolidityGrpc.WalletSolidityImplBase;
import org.stabila.common.application.Service;
import org.stabila.common.crypto.SignInterface;
import org.stabila.common.crypto.SignUtils;
import org.stabila.common.overlay.discover.node.NodeHandler;
import org.stabila.common.overlay.discover.node.NodeManager;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.common.utils.StringUtil;
import org.stabila.common.utils.Utils;
import org.stabila.core.ChainBaseManager;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.capsule.WitnessCapsule;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.BadItemException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.ItemNotFoundException;
import org.stabila.core.exception.NonUniqueObjectException;
import org.stabila.core.exception.StoreException;
import org.stabila.core.exception.VMIllegalException;
import org.stabila.core.exception.ZksnarkException;
import org.stabila.core.metrics.MetricsApiService;
import org.stabila.core.services.filter.LiteFnQueryGrpcInterceptor;
import org.stabila.core.services.ratelimiter.RateLimiterInterceptor;
import org.stabila.core.utils.TransactionUtil;
import org.stabila.core.zen.address.DiversifierT;
import org.stabila.core.zen.address.IncomingViewingKey;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.Protocol.DynamicProperties;
import org.stabila.protos.Protocol.Exchange;
import org.stabila.protos.Protocol.MarketOrder;
import org.stabila.protos.Protocol.MarketOrderList;
import org.stabila.protos.Protocol.MarketOrderPair;
import org.stabila.protos.Protocol.MarketOrderPairList;
import org.stabila.protos.Protocol.MarketPriceList;
import org.stabila.protos.Protocol.NodeInfo;
import org.stabila.protos.Protocol.Proposal;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.Protocol.TransactionSign;
import org.stabila.protos.contract.AccountContract.AccountCreateContract;
import org.stabila.protos.contract.AccountContract.AccountPermissionUpdateContract;
import org.stabila.protos.contract.AccountContract.AccountUpdateContract;
import org.stabila.protos.contract.AccountContract.SetAccountIdContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.ParticipateAssetIssueContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.UnfreezeAssetContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.UpdateAssetContract;
import org.stabila.protos.contract.BalanceContract.AccountBalanceRequest;
import org.stabila.protos.contract.BalanceContract.AccountBalanceResponse;
import org.stabila.protos.contract.BalanceContract.BlockBalanceTrace;
import org.stabila.protos.contract.BalanceContract.FreezeBalanceContract;
import org.stabila.protos.contract.BalanceContract.TransferContract;
import org.stabila.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.stabila.protos.contract.BalanceContract.WithdrawBalanceContract;
import org.stabila.protos.contract.ExchangeContract.ExchangeCreateContract;
import org.stabila.protos.contract.ExchangeContract.ExchangeInjectContract;
import org.stabila.protos.contract.ExchangeContract.ExchangeTransactionContract;
import org.stabila.protos.contract.ExchangeContract.ExchangeWithdrawContract;
import org.stabila.protos.contract.MarketContract.MarketCancelOrderContract;
import org.stabila.protos.contract.MarketContract.MarketSellAssetContract;
import org.stabila.protos.contract.ProposalContract.ProposalApproveContract;
import org.stabila.protos.contract.ProposalContract.ProposalCreateContract;
import org.stabila.protos.contract.ProposalContract.ProposalDeleteContract;
import org.stabila.protos.contract.ShieldContract.IncrementalMerkleVoucherInfo;
import org.stabila.protos.contract.ShieldContract.OutputPointInfo;
import org.stabila.protos.contract.SmartContractOuterClass.ClearABIContract;
import org.stabila.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContractDataWrapper;
import org.stabila.protos.contract.SmartContractOuterClass.TriggerSmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.UpdateEnergyLimitContract;
import org.stabila.protos.contract.SmartContractOuterClass.UpdateSettingContract;
import org.stabila.protos.contract.StorageContract.UpdateBrokerageContract;
import org.stabila.protos.contract.WitnessContract.VoteWitnessContract;
import org.stabila.protos.contract.WitnessContract.WitnessCreateContract;
import org.stabila.protos.contract.WitnessContract.WitnessUpdateContract;

@Component
@Slf4j(topic = "API")
public class RpcApiService implements Service {

  public static final String CONTRACT_VALIDATE_EXCEPTION = "ContractValidateException: {}";
  private static final String EXCEPTION_CAUGHT = "exception caught";
  private static final long BLOCK_LIMIT_NUM = 100;
  private static final long TRANSACTION_LIMIT_NUM = 1000;
  private int port = Args.getInstance().getRpcPort();
  private Server apiServer;
  @Autowired
  private Manager dbManager;

  @Autowired
  private ChainBaseManager chainBaseManager;

  @Autowired
  private NodeManager nodeManager;
  @Autowired
  private Wallet wallet;

  @Autowired
  private TransactionUtil transactionUtil;

  @Autowired
  private NodeInfoService nodeInfoService;
  @Autowired
  private RateLimiterInterceptor rateLimiterInterceptor;
  @Autowired
  private LiteFnQueryGrpcInterceptor liteFnQueryGrpcInterceptor;

  @Autowired
  private MetricsApiService metricsApiService;

  @Getter
  private DatabaseApi databaseApi = new DatabaseApi();
  private WalletApi walletApi = new WalletApi();
  @Getter
  private WalletSolidityApi walletSolidityApi = new WalletSolidityApi();
  @Getter
  private MonitorApi monitorApi = new MonitorApi();

  @Override
  public void init() {

  }

  @Override
  public void init(CommonParameter args) {
  }

  @Override
  public void start() {
    try {
      NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port).addService(databaseApi);
      CommonParameter parameter = Args.getInstance();

      if (parameter.getRpcThreadNum() > 0) {
        serverBuilder = serverBuilder
            .executor(Executors.newFixedThreadPool(parameter.getRpcThreadNum()));
      }

      if (parameter.isSolidityNode()) {
        serverBuilder = serverBuilder.addService(walletSolidityApi);
        if (parameter.isWalletExtensionApi()) {
          serverBuilder = serverBuilder.addService(new WalletExtensionApi());
        }
      } else {
        serverBuilder = serverBuilder.addService(walletApi);
      }

      if (parameter.isNodeMetricsEnable()) {
        serverBuilder = serverBuilder.addService(monitorApi);
      }

      // Set configs from config.conf or default value
      serverBuilder
          .maxConcurrentCallsPerConnection(parameter.getMaxConcurrentCallsPerConnection())
          .flowControlWindow(parameter.getFlowControlWindow())
          .maxConnectionIdle(parameter.getMaxConnectionIdleInMillis(), TimeUnit.MILLISECONDS)
          .maxConnectionAge(parameter.getMaxConnectionAgeInMillis(), TimeUnit.MILLISECONDS)
          .maxMessageSize(parameter.getMaxMessageSize())
          .maxHeaderListSize(parameter.getMaxHeaderListSize());

      // add a rate limiter interceptor
      serverBuilder.intercept(rateLimiterInterceptor);

      // add lite fullnode query interceptor
      serverBuilder.intercept(liteFnQueryGrpcInterceptor);

      apiServer = serverBuilder.build();
      rateLimiterInterceptor.init(apiServer);

      apiServer.start();
    } catch (IOException e) {
      logger.debug(e.getMessage(), e);
    }

    logger.info("RpcApiService has started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println("*** shutting down gRPC server since JVM is shutting down");
      //server.this.stop();
      System.err.println("*** server is shutdown");
    }));
  }


  private void callContract(TriggerSmartContract request,
      StreamObserver<TransactionExtention> responseObserver, boolean isConstant) {
    TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    try {
      TransactionCapsule stbCap = createTransactionCapsule(request,
          ContractType.TriggerSmartContract);
      Transaction stb;
      if (isConstant) {
        stb = wallet.triggerConstantContract(request, stbCap, stbExtBuilder, retBuilder);
      } else {
        stb = wallet.triggerContract(request, stbCap, stbExtBuilder, retBuilder);
      }
      stbExtBuilder.setTransaction(stb);
      stbExtBuilder.setTxid(stbCap.getTransactionId().getByteString());
      retBuilder.setResult(true).setCode(response_code.SUCCESS);
      stbExtBuilder.setResult(retBuilder);
    } catch (ContractValidateException | VMIllegalException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(Wallet.CONTRACT_VALIDATE_ERROR + e.getMessage()));
      stbExtBuilder.setResult(retBuilder);
      logger.warn(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
    } catch (RuntimeException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      stbExtBuilder.setResult(retBuilder);
      logger.warn("When run constant call in VM, have RuntimeException: " + e.getMessage());
    } catch (Exception e) {
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      stbExtBuilder.setResult(retBuilder);
      logger.warn("unknown exception caught: " + e.getMessage(), e);
    } finally {
      responseObserver.onNext(stbExtBuilder.build());
      responseObserver.onCompleted();
    }
  }

  private TransactionCapsule createTransactionCapsule(Message message,
                                                      ContractType contractType) throws ContractValidateException {
    return wallet.createTransactionCapsule(message, contractType);
  }


  private TransactionExtention transaction2Extention(Transaction transaction) {
    if (transaction == null) {
      return null;
    }
    TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    stbExtBuilder.setTransaction(transaction);
    stbExtBuilder.setTxid(Sha256Hash.of(CommonParameter.getInstance()
        .isECKeyCryptoEngine(), transaction.getRawData().toByteArray()).getByteString());
    retBuilder.setResult(true).setCode(response_code.SUCCESS);
    stbExtBuilder.setResult(retBuilder);
    return stbExtBuilder.build();
  }

  private BlockExtention block2Extention(Block block) {
    if (block == null) {
      return null;
    }
    BlockExtention.Builder builder = BlockExtention.newBuilder();
    BlockCapsule blockCapsule = new BlockCapsule(block);
    builder.setBlockHeader(block.getBlockHeader());
    builder.setBlockid(ByteString.copyFrom(blockCapsule.getBlockId().getBytes()));
    for (int i = 0; i < block.getTransactionsCount(); i++) {
      Transaction transaction = block.getTransactions(i);
      builder.addTransactions(transaction2Extention(transaction));
    }
    return builder.build();
  }

  private StatusRuntimeException getRunTimeException(Exception e) {
    if (e != null) {
      return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
    } else {
      return Status.INTERNAL.withDescription("unknown").asRuntimeException();
    }
  }

  private void checkSupportShieldedTransaction() throws ZksnarkException {
    String msg = "Not support Shielded Transaction, need to be opened by the committee";
    if (!dbManager.getDynamicPropertiesStore().supportShieldedTransaction()) {
      throw new ZksnarkException(msg);
    }
  }

  private void checkSupportShieldedTRC20Transaction() throws ZksnarkException {
    String msg = "Not support Shielded TRC20 Transaction, need to be opened by the committee";
    if (!dbManager.getDynamicPropertiesStore().supportShieldedTRC20Transaction()) {
      throw new ZksnarkException(msg);
    }
  }

  @Override
  public void stop() {
    if (apiServer != null) {
      apiServer.shutdown();
    }
  }

  /**
   * ...
   */
  public void blockUntilShutdown() {
    if (apiServer != null) {
      try {
        apiServer.awaitTermination();
      } catch (InterruptedException e) {
        logger.warn("{}", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * DatabaseApi.
   */
  public class DatabaseApi extends DatabaseImplBase {

    @Override
    public void getBlockReference(org.stabila.api.GrpcAPI.EmptyMessage request,
        StreamObserver<org.stabila.api.GrpcAPI.BlockReference> responseObserver) {
      long headBlockNum = dbManager.getDynamicPropertiesStore()
          .getLatestBlockHeaderNumber();
      byte[] blockHeaderHash = dbManager.getDynamicPropertiesStore()
          .getLatestBlockHeaderHash().getBytes();
      BlockReference ref = BlockReference.newBuilder()
          .setBlockHash(ByteString.copyFrom(blockHeaderHash))
          .setBlockNum(headBlockNum)
          .build();
      responseObserver.onNext(ref);
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      Block block = null;
      try {
        block = chainBaseManager.getHead().getInstance();
      } catch (StoreException e) {
        logger.error(e.getMessage());
      }
      responseObserver.onNext(block);
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      Block block = null;
      try {
        block = chainBaseManager.getBlockByNum(request.getNum()).getInstance();
      } catch (StoreException e) {
        logger.error(e.getMessage());
      }
      responseObserver.onNext(block);
      responseObserver.onCompleted();
    }

    @Override
    public void getDynamicProperties(EmptyMessage request,
        StreamObserver<DynamicProperties> responseObserver) {
      DynamicProperties.Builder builder = DynamicProperties.newBuilder();
      builder.setLastSolidityBlockNum(
          dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
      DynamicProperties dynamicProperties = builder.build();
      responseObserver.onNext(dynamicProperties);
      responseObserver.onCompleted();
    }
  }

  /**
   * WalletSolidityApi.
   */
  public class WalletSolidityApi extends WalletSolidityImplBase {

    @Override
    public void getAccount(Account request, StreamObserver<Account> responseObserver) {
      ByteString addressBs = request.getAddress();
      if (addressBs != null) {
        Account reply = wallet.getAccount(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountById(Account request, StreamObserver<Account> responseObserver) {
      ByteString id = request.getAccountId();
      if (id != null) {
        Account reply = wallet.getAccountById(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void listWitnesses(EmptyMessage request, StreamObserver<WitnessList> responseObserver) {
      responseObserver.onNext(wallet.getWitnessList());
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueList(EmptyMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList());
      responseObserver.onCompleted();
    }

    @Override
    public void getPaginatedAssetIssueList(PaginatedMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueByName(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetName = request.getValue();
      if (assetName != null) {
        try {
          responseObserver.onNext(wallet.getAssetIssueByName(assetName));
        } catch (NonUniqueObjectException e) {
          responseObserver.onNext(null);
          logger.error("Solidity NonUniqueObjectException: {}", e.getMessage());
        }
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString assetName = request.getValue();

      if (assetName != null) {
        responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueById(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetId = request.getValue();

      if (assetId != null) {
        responseObserver.onNext(wallet.getAssetIssueById(assetId.toStringUtf8()));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getNowBlock());
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock2(EmptyMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      responseObserver.onNext(block2Extention(wallet.getNowBlock()));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      long num = request.getNum();
      if (num >= 0) {
        Block reply = wallet.getBlockByNum(num);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum2(NumberMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      long num = request.getNum();
      if (num >= 0) {
        Block reply = wallet.getBlockByNum(num);
        responseObserver.onNext(block2Extention(reply));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }


    @Override
    public void getDelegatedResource(DelegatedResourceMessage request,
        StreamObserver<DelegatedResourceList> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
      responseObserver.onCompleted();
    }

    @Override
    public void getDelegatedResourceAccountIndex(BytesMessage request,
        StreamObserver<org.stabila.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
      responseObserver.onCompleted();
    }

    @Override
    public void getExchangeById(BytesMessage request,
        StreamObserver<Exchange> responseObserver) {
      ByteString exchangeId = request.getValue();

      if (Objects.nonNull(exchangeId)) {
        responseObserver.onNext(wallet.getExchangeById(exchangeId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void listExchanges(EmptyMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver.onNext(wallet.getExchangeList());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      getTransactionCountByBlockNumCommon(request, responseObserver);
    }

    @Override
    public void getTransactionById(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        Transaction reply = wallet.getTransactionById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionInfoById(BytesMessage request,
        StreamObserver<TransactionInfo> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        TransactionInfo reply = wallet.getTransactionInfoById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void generateAddress(EmptyMessage request,
        StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
      generateAddressCommon(request, responseObserver);
    }

    @Override
    public void getRewardInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      getRewardInfoCommon(request, responseObserver);
    }

    @Override
    public void getBrokerageInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      getBrokerageInfoCommon(request, responseObserver);
    }

    @Override
    public void getBurnStb(EmptyMessage request, StreamObserver<NumberMessage> responseObserver) {
      getBurnStbCommon(request, responseObserver);
    }

    @Override
    public void getMerkleTreeVoucherInfo(OutputPointInfo request,
        StreamObserver<IncrementalMerkleVoucherInfo> responseObserver) {

      try {
        checkSupportShieldedTransaction();

        IncrementalMerkleVoucherInfo witnessInfo = wallet
            .getMerkleTreeVoucherInfo(request);
        responseObserver.onNext(witnessInfo);
      } catch (Exception ex) {
        responseObserver.onError(getRunTimeException(ex));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanNoteByIvk(GrpcAPI.IvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();

      try {
        checkSupportShieldedTransaction();

        DecryptNotes decryptNotes = wallet
            .scanNoteByIvk(startNum, endNum, request.getIvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanAndMarkNoteByIvk(GrpcAPI.IvkDecryptAndMarkParameters request,
        StreamObserver<GrpcAPI.DecryptNotesMarked> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();

      try {
        checkSupportShieldedTransaction();

        DecryptNotesMarked decryptNotes = wallet.scanAndMarkNoteByIvk(startNum, endNum,
            request.getIvk().toByteArray(),
            request.getAk().toByteArray(),
            request.getNk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException | InvalidProtocolBufferException
          | ItemNotFoundException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanNoteByOvk(GrpcAPI.OvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        checkSupportShieldedTransaction();

        DecryptNotes decryptNotes = wallet
            .scanNoteByOvk(startNum, endNum, request.getOvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void isSpend(NoteParameters request, StreamObserver<SpendResult> responseObserver) {
      try {
        checkSupportShieldedTransaction();

        responseObserver.onNext(wallet.isSpend(request));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanShieldedTRC20NotesByIvk(IvkDecryptTRC20Parameters request,
        StreamObserver<DecryptNotesTRC20> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      byte[] contractAddress = request.getShieldedTRC20ContractAddress().toByteArray();
      byte[] ivk = request.getIvk().toByteArray();
      byte[] ak = request.getAk().toByteArray();
      byte[] nk = request.getNk().toByteArray();
      ProtocolStringList topicsList = request.getEventsList();

      try {
        checkSupportShieldedTRC20Transaction();
        responseObserver.onNext(
            wallet.scanShieldedTRC20NotesByIvk(startNum, endNum, contractAddress, ivk, ak, nk,
                topicsList));

      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanShieldedTRC20NotesByOvk(OvkDecryptTRC20Parameters request,
        StreamObserver<DecryptNotesTRC20> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      byte[] contractAddress = request.getShieldedTRC20ContractAddress().toByteArray();
      byte[] ovk = request.getOvk().toByteArray();
      ProtocolStringList topicList = request.getEventsList();
      try {
        checkSupportShieldedTRC20Transaction();
        responseObserver
            .onNext(wallet
                .scanShieldedTRC20NotesByOvk(startNum, endNum, ovk, contractAddress, topicList));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void isShieldedTRC20ContractNoteSpent(NfTRC20Parameters request,
        StreamObserver<GrpcAPI.NullifierResult> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();
        responseObserver.onNext(wallet.isShieldedTRC20ContractNoteSpent(request));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketOrderByAccount(BytesMessage request,
        StreamObserver<MarketOrderList> responseObserver) {
      try {
        ByteString address = request.getValue();

        MarketOrderList marketOrderList = wallet
            .getMarketOrderByAccount(address);
        responseObserver.onNext(marketOrderList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketOrderById(BytesMessage request,
        StreamObserver<MarketOrder> responseObserver) {
      try {
        ByteString address = request.getValue();

        MarketOrder marketOrder = wallet
            .getMarketOrderById(address);
        responseObserver.onNext(marketOrder);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketPriceByPair(MarketOrderPair request,
        StreamObserver<MarketPriceList> responseObserver) {
      try {
        MarketPriceList marketPriceList = wallet
            .getMarketPriceByPair(request.getSellTokenId().toByteArray(),
                request.getBuyTokenId().toByteArray());
        responseObserver.onNext(marketPriceList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketOrderListByPair(org.stabila.protos.Protocol.MarketOrderPair request,
        StreamObserver<MarketOrderList> responseObserver) {
      try {
        MarketOrderList orderPairList = wallet
            .getMarketOrderListByPair(request.getSellTokenId().toByteArray(),
                request.getBuyTokenId().toByteArray());
        responseObserver.onNext(orderPairList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketPairList(EmptyMessage request,
        StreamObserver<MarketOrderPairList> responseObserver) {
      try {
        MarketOrderPairList pairList = wallet.getMarketPairList();
        responseObserver.onNext(pairList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void triggerConstantContract(TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, true);
    }

    @Override
    public void getTransactionInfoByBlockNum(NumberMessage request,
        StreamObserver<TransactionInfoList> responseObserver) {
      try {
        responseObserver.onNext(wallet.getTransactionInfoByBlockNum(request.getNum()));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }

      responseObserver.onCompleted();
    }
  }

  /**
   * WalletExtensionApi.
   */
  public class WalletExtensionApi extends WalletExtensionGrpc.WalletExtensionImplBase {

    private TransactionListExtention transactionList2Extention(TransactionList transactionList) {
      if (transactionList == null) {
        return null;
      }
      TransactionListExtention.Builder builder = TransactionListExtention.newBuilder();
      for (Transaction transaction : transactionList.getTransactionList()) {
        builder.addTransaction(transaction2Extention(transaction));
      }
      return builder.build();
    }
  }

  /**
   * WalletApi.
   */
  public class WalletApi extends WalletImplBase {

    private BlockListExtention blockList2Extention(BlockList blockList) {
      if (blockList == null) {
        return null;
      }
      BlockListExtention.Builder builder = BlockListExtention.newBuilder();
      for (Block block : blockList.getBlockList()) {
        builder.addBlock(block2Extention(block));
      }
      return builder.build();
    }

    @Override
    public void getAccount(Account req, StreamObserver<Account> responseObserver) {
      ByteString addressBs = req.getAddress();
      if (addressBs != null) {
        Account reply = wallet.getAccount(req);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountById(Account req, StreamObserver<Account> responseObserver) {
      ByteString accountId = req.getAccountId();
      if (accountId != null) {
        Account reply = wallet.getAccountById(req);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    /**
     *
     */
    public void getAccountBalance(AccountBalanceRequest request,
        StreamObserver<AccountBalanceResponse> responseObserver) {
      try {
        AccountBalanceResponse accountBalanceResponse = wallet.getAccountBalance(request);
        responseObserver.onNext(accountBalanceResponse);
        responseObserver.onCompleted();
      } catch (Exception e) {
        responseObserver.onError(e);
      }
    }

    /**
     *
     */
    public void getBlockBalanceTrace(BlockBalanceTrace.BlockIdentifier request,
        StreamObserver<BlockBalanceTrace> responseObserver) {
      try {
        BlockBalanceTrace blockBalanceTrace = wallet.getBlockBalance(request);
        responseObserver.onNext(blockBalanceTrace);
        responseObserver.onCompleted();
      } catch (Exception e) {
        responseObserver.onError(e);
      }
    }

    @Override
    public void createTransaction(TransferContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver
            .onNext(
                createTransactionCapsule(request, ContractType.TransferContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createTransaction2(TransferContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.TransferContract, responseObserver);
    }

    private void createTransactionExtention(Message request, ContractType contractType,
        StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule stb = createTransactionCapsule(request, contractType);
        stbExtBuilder.setTransaction(stb.getInstance());
        stbExtBuilder.setTxid(stb.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (ContractValidateException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString
                .copyFromUtf8(Wallet.CONTRACT_VALIDATE_ERROR + e.getMessage()));
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info(EXCEPTION_CAUGHT + e.getMessage());
      }
      stbExtBuilder.setResult(retBuilder);
      responseObserver.onNext(stbExtBuilder.build());
      responseObserver.onCompleted();
    }


    @Override
    public void getTransactionSign(TransactionSign req,
        StreamObserver<Transaction> responseObserver) {
      TransactionCapsule result = TransactionUtil.getTransactionSign(req);
      responseObserver.onNext(result.getInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionSign2(TransactionSign req,
        StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule stb = TransactionUtil.getTransactionSign(req);
        stbExtBuilder.setTransaction(stb.getInstance());
        stbExtBuilder.setTxid(stb.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info(EXCEPTION_CAUGHT + e.getMessage());
      }
      stbExtBuilder.setResult(retBuilder);
      responseObserver.onNext(stbExtBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void addSign(TransactionSign req,
        StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule stb = transactionUtil.addSign(req);
        stbExtBuilder.setTransaction(stb.getInstance());
        stbExtBuilder.setTxid(stb.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info(EXCEPTION_CAUGHT + e.getMessage());
      }
      stbExtBuilder.setResult(retBuilder);
      responseObserver.onNext(stbExtBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionSignWeight(Transaction req,
        StreamObserver<TransactionSignWeight> responseObserver) {
      TransactionSignWeight tsw = transactionUtil.getTransactionSignWeight(req);
      responseObserver.onNext(tsw);
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionApprovedList(Transaction req,
        StreamObserver<TransactionApprovedList> responseObserver) {
      TransactionApprovedList tal = wallet.getTransactionApprovedList(req);
      responseObserver.onNext(tal);
      responseObserver.onCompleted();
    }

    @Override
    public void createAddress(BytesMessage req,
        StreamObserver<BytesMessage> responseObserver) {
      byte[] address = wallet.createAddress(req.getValue().toByteArray());
      BytesMessage.Builder builder = BytesMessage.newBuilder();
      builder.setValue(ByteString.copyFrom(address));
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    private EasyTransferResponse easyTransfer(byte[] privateKey, ByteString toAddress,
        long amount) {
      TransactionCapsule transactionCapsule;
      GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
      EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
      try {
        SignInterface cryptoEngine = SignUtils.fromPrivate(privateKey, Args.getInstance()
            .isECKeyCryptoEngine());
        byte[] owner = cryptoEngine.getAddress();
        TransferContract.Builder builder = TransferContract.newBuilder();
        builder.setOwnerAddress(ByteString.copyFrom(owner));
        builder.setToAddress(toAddress);
        builder.setAmount(amount);
        transactionCapsule = createTransactionCapsule(builder.build(),
            ContractType.TransferContract);
        transactionCapsule.sign(privateKey);
        GrpcAPI.Return result = wallet.broadcastTransaction(transactionCapsule.getInstance());
        responseBuild.setTransaction(transactionCapsule.getInstance());
        responseBuild.setTxid(transactionCapsule.getTransactionId().getByteString());
        responseBuild.setResult(result);
      } catch (ContractValidateException e) {
        returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      } catch (Exception e) {
        returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      }

      return responseBuild.build();
    }

    @Override
    public void easyTransfer(EasyTransferMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
      EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferAsset(EasyTransferAssetMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
      EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(),
          req.getAssetId(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    private EasyTransferResponse easyTransferAsset(byte[] privateKey, ByteString toAddress,
        String assetId, long amount) {
      TransactionCapsule transactionCapsule;
      GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
      EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
      try {
        SignInterface cryptoEngine = SignUtils.fromPrivate(privateKey,
            Args.getInstance().isECKeyCryptoEngine());
        byte[] owner = cryptoEngine.getAddress();
        TransferAssetContract.Builder builder = TransferAssetContract.newBuilder();
        builder.setOwnerAddress(ByteString.copyFrom(owner));
        builder.setToAddress(toAddress);
        builder.setAssetName(ByteString.copyFrom(assetId.getBytes()));
        builder.setAmount(amount);
        transactionCapsule = createTransactionCapsule(builder.build(),
            ContractType.TransferAssetContract);
        transactionCapsule.sign(privateKey);
        GrpcAPI.Return result = wallet.broadcastTransaction(transactionCapsule.getInstance());
        responseBuild.setTransaction(transactionCapsule.getInstance());
        responseBuild.setTxid(transactionCapsule.getTransactionId().getByteString());
        responseBuild.setResult(result);
      } catch (ContractValidateException e) {
        returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      } catch (Exception e) {
        returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      }

      return responseBuild.build();
    }

    @Override
    public void easyTransferByPrivate(EasyTransferByPrivateMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = req.getPrivateKey().toByteArray();
      EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferAssetByPrivate(EasyTransferAssetByPrivateMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = req.getPrivateKey().toByteArray();
      EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(),
          req.getAssetId(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void broadcastTransaction(Transaction req,
        StreamObserver<GrpcAPI.Return> responseObserver) {
      GrpcAPI.Return result = wallet.broadcastTransaction(req);
      responseObserver.onNext(result);
      responseObserver.onCompleted();
    }

    @Override
    public void createAssetIssue(AssetIssueContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AssetIssueContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createAssetIssue2(AssetIssueContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AssetIssueContract, responseObserver);
    }

    @Override
    public void unfreezeAsset(UnfreezeAssetContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.UnfreezeAssetContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void unfreezeAsset2(UnfreezeAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UnfreezeAssetContract, responseObserver);
    }

    //refactor、test later
    private void checkVoteWitnessAccount(VoteWitnessContract req) {
      //send back to cli
      ByteString ownerAddress = req.getOwnerAddress();
      Preconditions.checkNotNull(ownerAddress, "OwnerAddress is null");

      AccountCapsule account = dbManager.getAccountStore().get(ownerAddress.toByteArray());
      Preconditions.checkNotNull(account,
          "OwnerAddress[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      int votesCount = req.getVotesCount();
      Preconditions.checkArgument(votesCount <= 0, "VotesCount[" + votesCount + "] <= 0");
      if (dbManager.getDynamicPropertiesStore().supportAllowNewResourceModel()) {
        Preconditions.checkArgument(account.getAllStabilaPower() < votesCount,
            "stabila power[" + account.getAllStabilaPower() + "] <  VotesCount[" + votesCount + "]");
      } else {
        Preconditions.checkArgument(account.getStabilaPower() < votesCount,
            "stabila power[" + account.getStabilaPower() + "] <  VotesCount[" + votesCount + "]");
      }

      req.getVotesList().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        WitnessCapsule witness = dbManager.getWitnessStore()
            .get(voteAddress.toByteArray());
        String readableWitnessAddress = StringUtil.createReadableString(voteAddress);

        Preconditions.checkNotNull(witness, "witness[" + readableWitnessAddress + "] not exists");
        Preconditions.checkArgument(vote.getVoteCount() <= 0,
            "VoteAddress[" + readableWitnessAddress + "], VotesCount[" + vote
                .getVoteCount() + "] <= 0");
      });
    }

    @Override
    public void voteWitnessAccount(VoteWitnessContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.VoteWitnessContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void voteWitnessAccount2(VoteWitnessContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.VoteWitnessContract, responseObserver);
    }

    @Override
    public void updateSetting(UpdateSettingContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateSettingContract,
          responseObserver);
    }

    @Override
    public void updateEnergyLimit(UpdateEnergyLimitContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateEnergyLimitContract,
          responseObserver);
    }

    @Override
    public void clearContractABI(ClearABIContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ClearABIContract,
          responseObserver);
    }

    @Override
    public void createWitness(WitnessCreateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WitnessCreateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createWitness2(WitnessCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WitnessCreateContract, responseObserver);
    }

    @Override
    public void createAccount(AccountCreateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AccountCreateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createAccount2(AccountCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountCreateContract, responseObserver);
    }

    @Override
    public void updateWitness(WitnessUpdateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WitnessUpdateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateWitness2(WitnessUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WitnessUpdateContract, responseObserver);
    }

    @Override
    public void updateAccount(AccountUpdateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AccountUpdateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void setAccountId(SetAccountIdContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.SetAccountIdContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateAccount2(AccountUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountUpdateContract, responseObserver);
    }

    @Override
    public void updateAsset(UpdateAssetContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request,
                ContractType.UpdateAssetContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateAsset2(UpdateAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateAssetContract, responseObserver);
    }

    @Override
    public void freezeBalance(FreezeBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.FreezeBalanceContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void freezeBalance2(FreezeBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.FreezeBalanceContract, responseObserver);
    }

    @Override
    public void unfreezeBalance(UnfreezeBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.UnfreezeBalanceContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void unfreezeBalance2(UnfreezeBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UnfreezeBalanceContract, responseObserver);
    }

    @Override
    public void withdrawBalance(WithdrawBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WithdrawBalanceContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void withdrawBalance2(WithdrawBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WithdrawBalanceContract, responseObserver);
    }

    @Override
    public void proposalCreate(ProposalCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalCreateContract, responseObserver);
    }


    @Override
    public void proposalApprove(ProposalApproveContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalApproveContract, responseObserver);
    }

    @Override
    public void proposalDelete(ProposalDeleteContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalDeleteContract, responseObserver);
    }

    @Override
    public void exchangeCreate(ExchangeCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeCreateContract, responseObserver);
    }


    @Override
    public void exchangeInject(ExchangeInjectContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeInjectContract, responseObserver);
    }

    @Override
    public void exchangeWithdraw(ExchangeWithdrawContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeWithdrawContract, responseObserver);
    }

    @Override
    public void exchangeTransaction(ExchangeTransactionContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeTransactionContract,
          responseObserver);
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getNowBlock());
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock2(EmptyMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      Block block = wallet.getNowBlock();
      responseObserver.onNext(block2Extention(block));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getBlockByNum(request.getNum()));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum2(NumberMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      Block block = wallet.getBlockByNum(request.getNum());
      responseObserver.onNext(block2Extention(block));
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      getTransactionCountByBlockNumCommon(request, responseObserver);
    }

    @Override
    public void listNodes(EmptyMessage request, StreamObserver<NodeList> responseObserver) {
      List<NodeHandler> handlerList = nodeManager.dumpActiveNodes();

      Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();
      for (NodeHandler handler : handlerList) {
        String key = handler.getNode().getHexId() + handler.getNode().getHost();
        nodeHandlerMap.put(key, handler);
      }

      NodeList.Builder nodeListBuilder = NodeList.newBuilder();

      nodeHandlerMap.entrySet().stream()
          .forEach(v -> {
            org.stabila.common.overlay.discover.node.Node node = v.getValue().getNode();
            nodeListBuilder.addNodes(Node.newBuilder().setAddress(
                Address.newBuilder()
                    .setHost(ByteString.copyFrom(ByteArray.fromString(node.getHost())))
                    .setPort(node.getPort())));
          });
      responseObserver.onNext(nodeListBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void transferAsset(TransferAssetContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver
            .onNext(createTransactionCapsule(request, ContractType.TransferAssetContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void transferAsset2(TransferAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.TransferAssetContract, responseObserver);
    }

    @Override
    public void participateAssetIssue(ParticipateAssetIssueContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver
            .onNext(createTransactionCapsule(request, ContractType.ParticipateAssetIssueContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void participateAssetIssue2(ParticipateAssetIssueContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ParticipateAssetIssueContract,
          responseObserver);
    }

    @Override
    public void getAssetIssueByAccount(Account request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAssetIssueByAccount(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountNet(Account request,
        StreamObserver<AccountNetMessage> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAccountNet(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountResource(Account request,
        StreamObserver<AccountResourceMessage> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAccountResource(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueByName(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetName = request.getValue();
      if (assetName != null) {
        try {
          responseObserver.onNext(wallet.getAssetIssueByName(assetName));
        } catch (NonUniqueObjectException e) {
          responseObserver.onNext(null);
          logger.debug("FullNode NonUniqueObjectException: {}", e.getMessage());
        }
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString assetName = request.getValue();

      if (assetName != null) {
        responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueById(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetId = request.getValue();

      if (assetId != null) {
        responseObserver.onNext(wallet.getAssetIssueById(assetId.toStringUtf8()));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockById(BytesMessage request, StreamObserver<Block> responseObserver) {
      ByteString blockId = request.getValue();

      if (Objects.nonNull(blockId)) {
        responseObserver.onNext(wallet.getBlockById(blockId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getProposalById(BytesMessage request,
        StreamObserver<Proposal> responseObserver) {
      ByteString proposalId = request.getValue();

      if (Objects.nonNull(proposalId)) {
        responseObserver.onNext(wallet.getProposalById(proposalId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getExchangeById(BytesMessage request,
        StreamObserver<Exchange> responseObserver) {
      ByteString exchangeId = request.getValue();

      if (Objects.nonNull(exchangeId)) {
        responseObserver.onNext(wallet.getExchangeById(exchangeId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLimitNext(BlockLimit request,
        StreamObserver<BlockList> responseObserver) {
      long startNum = request.getStartNum();
      long endNum = request.getEndNum();

      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        responseObserver.onNext(wallet.getBlocksByLimitNext(startNum, endNum - startNum));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLimitNext2(BlockLimit request,
        StreamObserver<BlockListExtention> responseObserver) {
      long startNum = request.getStartNum();
      long endNum = request.getEndNum();

      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        responseObserver
            .onNext(blockList2Extention(wallet.getBlocksByLimitNext(startNum, endNum - startNum)));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLatestNum(NumberMessage request,
        StreamObserver<BlockList> responseObserver) {
      long getNum = request.getNum();

      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        responseObserver.onNext(wallet.getBlockByLatestNum(getNum));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLatestNum2(NumberMessage request,
        StreamObserver<BlockListExtention> responseObserver) {
      long getNum = request.getNum();

      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        responseObserver.onNext(blockList2Extention(wallet.getBlockByLatestNum(getNum)));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionById(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      ByteString transactionId = request.getValue();

      if (Objects.nonNull(transactionId)) {
        responseObserver.onNext(wallet.getTransactionById(transactionId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void deployContract(CreateSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.CreateSmartContract, responseObserver);
    }

    public void totalTransaction(EmptyMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      responseObserver.onNext(wallet.totalTransaction());
      responseObserver.onCompleted();
    }

    @Override
    public void getNextMaintenanceTime(EmptyMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      responseObserver.onNext(wallet.getNextMaintenanceTime());
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueList(EmptyMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList());
      responseObserver.onCompleted();
    }

    @Override
    public void triggerContract(TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, false);
    }

    @Override
    public void triggerConstantContract(TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, true);
    }

    private void callContract(TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver, boolean isConstant) {
      TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule stbCap = createTransactionCapsule(request,
            ContractType.TriggerSmartContract);
        Transaction stb;
        if (isConstant) {
          stb = wallet.triggerConstantContract(request, stbCap, stbExtBuilder, retBuilder);
        } else {
          stb = wallet.triggerContract(request, stbCap, stbExtBuilder, retBuilder);
        }
        stbExtBuilder.setTransaction(stb);
        stbExtBuilder.setTxid(stbCap.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
        stbExtBuilder.setResult(retBuilder);
      } catch (ContractValidateException | VMIllegalException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(Wallet
                .CONTRACT_VALIDATE_ERROR + e.getMessage()));
        stbExtBuilder.setResult(retBuilder);
        logger.warn(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      } catch (RuntimeException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        stbExtBuilder.setResult(retBuilder);
        logger.warn("When run constant call in VM, have Runtime Exception: " + e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        stbExtBuilder.setResult(retBuilder);
        logger.warn("unknown exception caught: " + e.getMessage(), e);
      } finally {
        responseObserver.onNext(stbExtBuilder.build());
        responseObserver.onCompleted();
      }
    }

    public void getPaginatedAssetIssueList(PaginatedMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();
    }

    @Override
    public void getContract(BytesMessage request,
        StreamObserver<SmartContract> responseObserver) {
      SmartContract contract = wallet.getContract(request);
      responseObserver.onNext(contract);
      responseObserver.onCompleted();
    }

    @Override
    public void getContractInfo(BytesMessage request,
        StreamObserver<SmartContractDataWrapper> responseObserver) {
      SmartContractDataWrapper contract = wallet.getContractInfo(request);
      responseObserver.onNext(contract);
      responseObserver.onCompleted();
    }

    public void listWitnesses(EmptyMessage request,
        StreamObserver<WitnessList> responseObserver) {
      responseObserver.onNext(wallet.getWitnessList());
      responseObserver.onCompleted();
    }

    @Override
    public void listProposals(EmptyMessage request,
        StreamObserver<ProposalList> responseObserver) {
      responseObserver.onNext(wallet.getProposalList());
      responseObserver.onCompleted();
    }


    @Override
    public void getDelegatedResource(DelegatedResourceMessage request,
        StreamObserver<DelegatedResourceList> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
      responseObserver.onCompleted();
    }

    public void getDelegatedResourceAccountIndex(BytesMessage request,
        StreamObserver<org.stabila.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
      responseObserver.onCompleted();
    }

    @Override
    public void getPaginatedProposalList(PaginatedMessage request,
        StreamObserver<ProposalList> responseObserver) {
      responseObserver
          .onNext(wallet.getPaginatedProposalList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();

    }

    @Override
    public void getPaginatedExchangeList(PaginatedMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver
          .onNext(wallet.getPaginatedExchangeList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();

    }

    @Override
    public void listExchanges(EmptyMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver.onNext(wallet.getExchangeList());
      responseObserver.onCompleted();
    }

    @Override
    public void getChainParameters(EmptyMessage request,
        StreamObserver<Protocol.ChainParameters> responseObserver) {
      responseObserver.onNext(wallet.getChainParameters());
      responseObserver.onCompleted();
    }

    @Override
    public void generateAddress(EmptyMessage request,
        StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
      generateAddressCommon(request, responseObserver);
    }

    @Override
    public void getTransactionInfoById(BytesMessage request,
        StreamObserver<TransactionInfo> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        TransactionInfo reply = wallet.getTransactionInfoById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getNodeInfo(EmptyMessage request, StreamObserver<NodeInfo> responseObserver) {
      try {
        responseObserver.onNext(nodeInfoService.getNodeInfo().transferToProtoEntity());
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void accountPermissionUpdate(AccountPermissionUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountPermissionUpdateContract,
          responseObserver);
    }

    @Override
    public void getMerkleTreeVoucherInfo(OutputPointInfo request,
        StreamObserver<IncrementalMerkleVoucherInfo> responseObserver) {

      try {
        checkSupportShieldedTransaction();

        IncrementalMerkleVoucherInfo witnessInfo = wallet
            .getMerkleTreeVoucherInfo(request);
        responseObserver.onNext(witnessInfo);
      } catch (Exception ex) {
        responseObserver.onError(getRunTimeException(ex));
        return;
      }

      responseObserver.onCompleted();
    }

    @Override
    public void createShieldedTransaction(PrivateParameters request,
        StreamObserver<TransactionExtention> responseObserver) {

      TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();

      try {
        checkSupportShieldedTransaction();

        TransactionCapsule stb = wallet.createShieldedTransaction(request);
        stbExtBuilder.setTransaction(stb.getInstance());
        stbExtBuilder.setTxid(stb.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (ContractValidateException | ZksnarkException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString
                .copyFromUtf8(Wallet.CONTRACT_VALIDATE_ERROR + e.getMessage()));
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("createShieldedTransaction exception caught: " + e.getMessage());
      }

      stbExtBuilder.setResult(retBuilder);
      responseObserver.onNext(stbExtBuilder.build());
      responseObserver.onCompleted();

    }

    @Override
    public void createShieldedTransactionWithoutSpendAuthSig(PrivateParametersWithoutAsk request,
        StreamObserver<TransactionExtention> responseObserver) {

      TransactionExtention.Builder stbExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();

      try {
        checkSupportShieldedTransaction();

        TransactionCapsule stb = wallet.createShieldedTransactionWithoutSpendAuthSig(request);
        stbExtBuilder.setTransaction(stb.getInstance());
        stbExtBuilder.setTxid(stb.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (ContractValidateException | ZksnarkException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString
                .copyFromUtf8(Wallet.CONTRACT_VALIDATE_ERROR + e.getMessage()));
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info(
            "createShieldedTransactionWithoutSpendAuthSig exception caught: " + e.getMessage());
      }

      stbExtBuilder.setResult(retBuilder);
      responseObserver.onNext(stbExtBuilder.build());
      responseObserver.onCompleted();

    }

    @Override
    public void getNewShieldedAddress(EmptyMessage request,
        StreamObserver<ShieldedAddressInfo> responseObserver) {

      try {
        checkSupportShieldedTRC20Transaction();

        responseObserver.onNext(wallet.getNewShieldedAddress());
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getSpendingKey(EmptyMessage request,
        StreamObserver<BytesMessage> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        responseObserver.onNext(wallet.getSpendingKey());
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getRcm(EmptyMessage request,
        StreamObserver<BytesMessage> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        responseObserver.onNext(wallet.getRcm());
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getExpandedSpendingKey(BytesMessage request,
        StreamObserver<ExpandedSpendingKeyMessage> responseObserver) {
      ByteString spendingKey = request.getValue();

      try {
        checkSupportShieldedTRC20Transaction();

        ExpandedSpendingKeyMessage response = wallet.getExpandedSpendingKey(spendingKey);
        responseObserver.onNext(response);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getAkFromAsk(BytesMessage request, StreamObserver<BytesMessage> responseObserver) {
      ByteString ak = request.getValue();

      try {
        checkSupportShieldedTRC20Transaction();

        responseObserver.onNext(wallet.getAkFromAsk(ak));
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getNkFromNsk(BytesMessage request, StreamObserver<BytesMessage> responseObserver) {
      ByteString nk = request.getValue();

      try {
        checkSupportShieldedTRC20Transaction();

        responseObserver.onNext(wallet.getNkFromNsk(nk));
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getIncomingViewingKey(ViewingKeyMessage request,
        StreamObserver<IncomingViewingKeyMessage> responseObserver) {
      ByteString ak = request.getAk();
      ByteString nk = request.getNk();

      try {
        checkSupportShieldedTRC20Transaction();

        responseObserver.onNext(wallet.getIncomingViewingKey(ak.toByteArray(), nk.toByteArray()));
      } catch (ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getDiversifier(EmptyMessage request,
        StreamObserver<DiversifierMessage> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        DiversifierMessage d = wallet.getDiversifier();
        responseObserver.onNext(d);
      } catch (ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }


    @Override
    public void getZenPaymentAddress(IncomingViewingKeyDiversifierMessage request,
        StreamObserver<PaymentAddressMessage> responseObserver) {
      IncomingViewingKeyMessage ivk = request.getIvk();
      DiversifierMessage d = request.getD();

      try {
        checkSupportShieldedTRC20Transaction();

        PaymentAddressMessage saplingPaymentAddressMessage =
            wallet.getPaymentAddress(new IncomingViewingKey(ivk.getIvk().toByteArray()),
                new DiversifierT(d.getD().toByteArray()));

        responseObserver.onNext(saplingPaymentAddressMessage);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();

    }

    @Override
    public void scanNoteByIvk(GrpcAPI.IvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();

      try {
        checkSupportShieldedTransaction();

        DecryptNotes decryptNotes = wallet
            .scanNoteByIvk(startNum, endNum, request.getIvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();

    }

    @Override
    public void scanAndMarkNoteByIvk(GrpcAPI.IvkDecryptAndMarkParameters request,
        StreamObserver<GrpcAPI.DecryptNotesMarked> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();

      try {
        checkSupportShieldedTransaction();

        DecryptNotesMarked decryptNotes = wallet.scanAndMarkNoteByIvk(startNum, endNum,
            request.getIvk().toByteArray(),
            request.getAk().toByteArray(),
            request.getNk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException | InvalidProtocolBufferException
          | ItemNotFoundException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanNoteByOvk(GrpcAPI.OvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();

      try {
        checkSupportShieldedTransaction();

        DecryptNotes decryptNotes = wallet
            .scanNoteByOvk(startNum, endNum, request.getOvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void isSpend(NoteParameters request, StreamObserver<SpendResult> responseObserver) {
      try {
        checkSupportShieldedTransaction();

        responseObserver.onNext(wallet.isSpend(request));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createShieldNullifier(GrpcAPI.NfParameters request,
        StreamObserver<GrpcAPI.BytesMessage> responseObserver) {
      try {
        checkSupportShieldedTransaction();

        BytesMessage nf = wallet
            .createShieldNullifier(request);
        responseObserver.onNext(nf);
      } catch (ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createSpendAuthSig(SpendAuthSigParameters request,
        StreamObserver<GrpcAPI.BytesMessage> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        BytesMessage spendAuthSig = wallet.createSpendAuthSig(request);
        responseObserver.onNext(spendAuthSig);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getShieldTransactionHash(Transaction request,
        StreamObserver<GrpcAPI.BytesMessage> responseObserver) {
      try {
        checkSupportShieldedTransaction();

        BytesMessage transactionHash = wallet.getShieldTransactionHash(request);
        responseObserver.onNext(transactionHash);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createShieldedContractParameters(
        PrivateShieldedTRC20Parameters request,
        StreamObserver<org.stabila.api.GrpcAPI.ShieldedTRC20Parameters> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        ShieldedTRC20Parameters shieldedTRC20Parameters = wallet
            .createShieldedContractParameters(request);
        responseObserver.onNext(shieldedTRC20Parameters);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        logger.info("createShieldedContractParameters exception caught: " + e.getMessage());
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createShieldedContractParametersWithoutAsk(
        PrivateShieldedTRC20ParametersWithoutAsk request,
        StreamObserver<org.stabila.api.GrpcAPI.ShieldedTRC20Parameters> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        ShieldedTRC20Parameters shieldedTRC20Parameters = wallet
            .createShieldedContractParametersWithoutAsk(request);
        responseObserver.onNext(shieldedTRC20Parameters);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        logger
            .info("createShieldedContractParametersWithoutAsk exception caught: " + e.getMessage());
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanShieldedTRC20NotesByIvk(
        IvkDecryptTRC20Parameters request,
        StreamObserver<org.stabila.api.GrpcAPI.DecryptNotesTRC20> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        checkSupportShieldedTRC20Transaction();

        DecryptNotesTRC20 decryptNotes = wallet.scanShieldedTRC20NotesByIvk(startNum, endNum,
            request.getShieldedTRC20ContractAddress().toByteArray(),
            request.getIvk().toByteArray(),
            request.getAk().toByteArray(),
            request.getNk().toByteArray(),
            request.getEventsList());
        responseObserver.onNext(decryptNotes);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        logger.info("scanShieldedTRC20NotesByIvk exception caught: " + e.getMessage());
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanShieldedTRC20NotesByOvk(
        OvkDecryptTRC20Parameters request,
        StreamObserver<org.stabila.api.GrpcAPI.DecryptNotesTRC20> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        checkSupportShieldedTRC20Transaction();

        DecryptNotesTRC20 decryptNotes = wallet.scanShieldedTRC20NotesByOvk(startNum, endNum,
            request.getOvk().toByteArray(),
            request.getShieldedTRC20ContractAddress().toByteArray(),
            request.getEventsList());
        responseObserver.onNext(decryptNotes);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        logger.info("scanShieldedTRC20NotesByOvk exception caught: " + e.getMessage());
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void isShieldedTRC20ContractNoteSpent(NfTRC20Parameters request,
        StreamObserver<GrpcAPI.NullifierResult> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        GrpcAPI.NullifierResult nf = wallet
            .isShieldedTRC20ContractNoteSpent(request);
        responseObserver.onNext(nf);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTriggerInputForShieldedTRC20Contract(
        ShieldedTRC20TriggerContractParameters request,
        StreamObserver<org.stabila.api.GrpcAPI.BytesMessage> responseObserver) {
      try {
        checkSupportShieldedTRC20Transaction();

        responseObserver.onNext(wallet.getTriggerInputForShieldedTRC20Contract(request));
      } catch (Exception e) {
        responseObserver.onError(e);
        return;
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getRewardInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      getRewardInfoCommon(request, responseObserver);
    }

    @Override
    public void getBrokerageInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      getBrokerageInfoCommon(request, responseObserver);
    }

    @Override
    public void getBurnStb(EmptyMessage request, StreamObserver<NumberMessage> responseObserver) {
      getBurnStbCommon(request, responseObserver);
    }

    @Override
    public void updateBrokerage(UpdateBrokerageContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateBrokerageContract,
          responseObserver);
    }

    @Override
    public void createCommonTransaction(Transaction request,
        StreamObserver<TransactionExtention> responseObserver) {
      Transaction.Contract contract = request.getRawData().getContract(0);
      createTransactionExtention(contract.getParameter(), contract.getType(),
          responseObserver);
    }

    @Override
    public void getTransactionInfoByBlockNum(NumberMessage request,
        StreamObserver<TransactionInfoList> responseObserver) {
      try {
        responseObserver.onNext(wallet.getTransactionInfoByBlockNum(request.getNum()));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }

      responseObserver.onCompleted();
    }

    @Override
    public void marketSellAsset(MarketSellAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.MarketSellAssetContract,
          responseObserver);
    }

    @Override
    public void marketCancelOrder(MarketCancelOrderContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.MarketCancelOrderContract, responseObserver);
    }

    @Override
    public void getMarketOrderByAccount(BytesMessage request,
        StreamObserver<MarketOrderList> responseObserver) {
      try {
        ByteString address = request.getValue();

        MarketOrderList marketOrderList = wallet
            .getMarketOrderByAccount(address);
        responseObserver.onNext(marketOrderList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketOrderById(BytesMessage request,
        StreamObserver<MarketOrder> responseObserver) {
      try {
        ByteString address = request.getValue();

        MarketOrder marketOrder = wallet
            .getMarketOrderById(address);
        responseObserver.onNext(marketOrder);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketPriceByPair(MarketOrderPair request,
        StreamObserver<MarketPriceList> responseObserver) {
      try {
        MarketPriceList marketPriceList = wallet
            .getMarketPriceByPair(request.getSellTokenId().toByteArray(),
                request.getBuyTokenId().toByteArray());
        responseObserver.onNext(marketPriceList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketOrderListByPair(org.stabila.protos.Protocol.MarketOrderPair request,
        StreamObserver<MarketOrderList> responseObserver) {
      try {
        MarketOrderList orderPairList = wallet
            .getMarketOrderListByPair(request.getSellTokenId().toByteArray(),
                request.getBuyTokenId().toByteArray());
        responseObserver.onNext(orderPairList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getMarketPairList(EmptyMessage request,
        StreamObserver<MarketOrderPairList> responseObserver) {
      try {
        MarketOrderPairList pairList = wallet.getMarketPairList();
        responseObserver.onNext(pairList);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionFromPending(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      getTransactionFromPendingCommon(request, responseObserver);
    }

    @Override
    public void getTransactionListFromPending(EmptyMessage request,
        StreamObserver<TransactionIdList> responseObserver) {
      getTransactionListFromPendingCommon(request, responseObserver);
    }

    @Override
    public void getPendingSize(EmptyMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      getPendingSizeCommon(request, responseObserver);
    }
  }

  public class MonitorApi extends MonitorGrpc.MonitorImplBase {

    @Override
    public void getStatsInfo(EmptyMessage request,
        StreamObserver<Protocol.MetricsInfo> responseObserver) {
      responseObserver.onNext(metricsApiService.getMetricProtoInfo());
      responseObserver.onCompleted();
    }
  }

  public void generateAddressCommon(EmptyMessage request,
      StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
    SignInterface cryptoEngine = SignUtils.getGeneratedRandomSign(Utils.getRandom(),
        Args.getInstance().isECKeyCryptoEngine());
    byte[] priKey = cryptoEngine.getPrivateKey();
    byte[] address = cryptoEngine.getAddress();
    String addressStr = StringUtil.encode58Check(address);
    String priKeyStr = Hex.encodeHexString(priKey);
    AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
    builder.setAddress(addressStr);
    builder.setPrivateKey(priKeyStr);
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  public void getRewardInfoCommon(BytesMessage request,
      StreamObserver<NumberMessage> responseObserver) {
    try {
      long value = dbManager.getMortgageService().queryReward(request.getValue().toByteArray());
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      builder.setNum(value);
      responseObserver.onNext(builder.build());
    } catch (Exception e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }

  public void getBurnStbCommon(EmptyMessage request,
      StreamObserver<NumberMessage> responseObserver) {
    try {
      long value = dbManager.getDynamicPropertiesStore().getBurnStbAmount();
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      builder.setNum(value);
      responseObserver.onNext(builder.build());
    } catch (Exception e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }

  public void getBrokerageInfoCommon(BytesMessage request,
      StreamObserver<NumberMessage> responseObserver) {
    try {
      long cycle = dbManager.getDynamicPropertiesStore().getCurrentCycleNumber();
      long value = dbManager.getDelegationStore()
          .getBrokerage(cycle, request.getValue().toByteArray());
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      builder.setNum(value);
      responseObserver.onNext(builder.build());
    } catch (Exception e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }

  public void getTransactionCountByBlockNumCommon(NumberMessage request,
      StreamObserver<NumberMessage> responseObserver) {
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    try {
      Block block = chainBaseManager.getBlockByNum(request.getNum()).getInstance();
      builder.setNum(block.getTransactionsCount());
    } catch (StoreException e) {
      logger.error(e.getMessage());
      builder.setNum(-1);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  public void getTransactionFromPendingCommon(BytesMessage request,
      StreamObserver<Transaction> responseObserver) {
    try {
      String txId = ByteArray.toHexString(request.getValue().toByteArray());
      TransactionCapsule transactionCapsule = dbManager.getTxFromPending(txId);
      responseObserver.onNext(transactionCapsule == null ? null : transactionCapsule.getInstance());
    } catch (Exception e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }

  public void getTransactionListFromPendingCommon(EmptyMessage request,
      StreamObserver<TransactionIdList> responseObserver) {
    try {
      TransactionIdList.Builder builder = TransactionIdList.newBuilder();
      builder.addAllTxId(dbManager.getTxListFromPending());
      responseObserver.onNext(builder.build());
    } catch (Exception e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }

  public void getPendingSizeCommon(EmptyMessage request,
      StreamObserver<NumberMessage> responseObserver) {
    try {
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      builder.setNum(dbManager.getPendingSize());
      responseObserver.onNext(builder.build());
    } catch (Exception e) {
      responseObserver.onError(e);
    }
    responseObserver.onCompleted();
  }
}
