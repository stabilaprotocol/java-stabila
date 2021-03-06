package org.stabila.core.db;

import static org.stabila.common.utils.Commons.adjustBalance;
import static org.stabila.protos.Protocol.Transaction.Contract.ContractType.TransferContract;
import static org.stabila.protos.Protocol.Transaction.Result.contractResult.SUCCESS;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.common.args.GenesisBlock;
import org.stabila.common.logsfilter.EventPluginLoader;
import org.stabila.common.logsfilter.FilterQuery;
import org.stabila.common.logsfilter.capsule.BlockLogTriggerCapsule;
import org.stabila.common.logsfilter.capsule.ContractTriggerCapsule;
import org.stabila.common.logsfilter.capsule.SolidityTriggerCapsule;
import org.stabila.common.logsfilter.capsule.TransactionLogTriggerCapsule;
import org.stabila.common.logsfilter.capsule.TriggerCapsule;
import org.stabila.common.logsfilter.trigger.ContractEventTrigger;
import org.stabila.common.logsfilter.trigger.ContractLogTrigger;
import org.stabila.common.logsfilter.trigger.ContractTrigger;
import org.stabila.common.logsfilter.trigger.Trigger;
import org.stabila.common.overlay.message.Message;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.runtime.RuntimeImpl;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Pair;
import org.stabila.common.utils.SessionOptional;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.common.zksnark.MerkleContainer;
import org.stabila.consensus.Consensus;
import org.stabila.consensus.base.Param.Miner;
import org.stabila.core.ChainBaseManager;
import org.stabila.core.Constant;
import org.stabila.core.actuator.ActuatorCreator;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockBalanceTraceCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.BlockCapsule.BlockId;
import org.stabila.core.capsule.BytesCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.capsule.TransactionInfoCapsule;
import org.stabila.core.capsule.TransactionRetCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.capsule.utils.TransactionUtil;
import org.stabila.core.config.Parameter.ChainConstant;
import org.stabila.core.config.args.Args;
import org.stabila.core.consensus.ProposalController;
import org.stabila.core.db.KhaosDatabase.KhaosBlock;
import org.stabila.core.db.accountstate.TrieService;
import org.stabila.core.db.accountstate.callback.AccountStateCallBack;
import org.stabila.core.db.api.AssetUpdateHelper;
import org.stabila.core.db.api.MoveAbiHelper;
import org.stabila.core.db2.ISession;
import org.stabila.core.db2.core.Chainbase;
import org.stabila.core.db2.core.IStabilaChainBase;
import org.stabila.core.db2.core.SnapshotManager;
import org.stabila.core.exception.AccountResourceInsufficientException;
import org.stabila.core.exception.BadBlockException;
import org.stabila.core.exception.BadItemException;
import org.stabila.core.exception.BadNumberBlockException;
import org.stabila.core.exception.BalanceInsufficientException;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractSizeNotEqualToOneException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.DupTransactionException;
import org.stabila.core.exception.ItemNotFoundException;
import org.stabila.core.exception.NonCommonBlockException;
import org.stabila.core.exception.ReceiptCheckErrException;
import org.stabila.core.exception.TaposException;
import org.stabila.core.exception.TooBigTransactionException;
import org.stabila.core.exception.TooBigTransactionResultException;
import org.stabila.core.exception.TransactionExpirationException;
import org.stabila.core.exception.UnLinkedBlockException;
import org.stabila.core.exception.VMIllegalException;
import org.stabila.core.exception.ValidateScheduleException;
import org.stabila.core.exception.ValidateSignatureException;
import org.stabila.core.exception.ZksnarkException;
import org.stabila.core.metrics.MetricsKey;
import org.stabila.core.metrics.MetricsUtil;
import org.stabila.core.service.MortgageService;
import org.stabila.core.store.AccountAssetStore;
import org.stabila.core.store.AccountIdIndexStore;
import org.stabila.core.store.AccountIndexStore;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.AssetIssueStore;
import org.stabila.core.store.AssetIssueV2Store;
import org.stabila.core.store.CodeStore;
import org.stabila.core.store.ContractStore;
import org.stabila.core.store.DelegatedResourceAccountIndexStore;
import org.stabila.core.store.DelegatedResourceStore;
import org.stabila.core.store.DelegationStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.ExchangeStore;
import org.stabila.core.store.ExchangeV2Store;
import org.stabila.core.store.IncrementalMerkleTreeStore;
import org.stabila.core.store.NullifierStore;
import org.stabila.core.store.ProposalStore;
import org.stabila.core.store.StorageRowStore;
import org.stabila.core.store.StoreFactory;
import org.stabila.core.store.TransactionHistoryStore;
import org.stabila.core.store.TransactionRetStore;
import org.stabila.core.store.VotesStore;
import org.stabila.core.store.ExecutiveScheduleStore;
import org.stabila.core.store.ExecutiveStore;
import org.stabila.core.utils.TransactionRegister;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract;
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.contract.BalanceContract;


@Slf4j(topic = "DB")
@Component
public class Manager {

  private static final int SHIELDED_TRANS_IN_BLOCK_COUNTS = 1;
  private static final String SAVE_BLOCK = "save block: ";
  private static final int SLEEP_TIME_OUT = 50;
  private static final int TX_ID_CACHE_SIZE = 100_000;
  private final int shieldedTransInPendingMaxCounts =
      Args.getInstance().getShieldedTransInPendingMaxCounts();
  @Getter
  @Setter
  public boolean eventPluginLoaded = false;
  private int maxTransactionPendingSize = Args.getInstance().getMaxTransactionPendingSize();
  @Autowired(required = false)
  @Getter
  private TransactionCache transactionCache;
  @Autowired
  private KhaosDatabase khaosDb;
  @Getter
  @Autowired
  private RevokingDatabase revokingStore;
  @Getter
  private SessionOptional session = SessionOptional.instance();
  @Getter
  @Setter
  private boolean isSyncMode;

  // map<Long, IncrementalMerkleTree>
  @Getter
  @Setter
  private String netType;
  @Getter
  @Setter
  private ProposalController proposalController;
  @Getter
  @Setter
  private MerkleContainer merkleContainer;
  private ExecutorService validateSignService;
  private boolean isRunRePushThread = true;
  private boolean isRunTriggerCapsuleProcessThread = true;
  private BlockingQueue<TransactionCapsule> pushTransactionQueue = new LinkedBlockingQueue<>();
  @Getter
  private Cache<Sha256Hash, Boolean> transactionIdCache = CacheBuilder
      .newBuilder().maximumSize(TX_ID_CACHE_SIZE).recordStats().build();
  @Autowired
  private AccountStateCallBack accountStateCallBack;
  @Autowired
  private TrieService trieService;
  private Set<String> ownerAddressSet = new HashSet<>();
  @Getter
  @Autowired
  private MortgageService mortgageService;
  @Autowired
  private Consensus consensus;
  @Autowired
  @Getter
  private ChainBaseManager chainBaseManager;
  // transactions cache
  private BlockingQueue<TransactionCapsule> pendingTransactions;
  @Getter
  private AtomicInteger shieldedTransInPendingCounts = new AtomicInteger(0);
  // transactions popped
  private List<TransactionCapsule> poppedTransactions =
      Collections.synchronizedList(Lists.newArrayList());
  // the capacity is equal to Integer.MAX_VALUE default
  private BlockingQueue<TransactionCapsule> rePushTransactions;
  private BlockingQueue<TriggerCapsule> triggerCapsuleQueue;

  /**
   * Cycle thread to rePush Transactions
   */
  private Runnable rePushLoop =
      () -> {
        while (isRunRePushThread) {
          TransactionCapsule tx = null;
          try {
            tx = getRePushTransactions().peek();
            if (tx != null) {
              this.rePush(tx);
            } else {
              TimeUnit.MILLISECONDS.sleep(SLEEP_TIME_OUT);
            }
          } catch (Throwable ex) {
            if (ex instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            logger.error("unknown exception happened in rePush loop", ex);
          } finally {
            if (tx != null) {
              getRePushTransactions().remove(tx);
            }
          }
        }
      };
  private Runnable triggerCapsuleProcessLoop =
      () -> {
        while (isRunTriggerCapsuleProcessThread) {
          try {
            TriggerCapsule triggerCapsule = triggerCapsuleQueue.poll(1, TimeUnit.SECONDS);
            if (triggerCapsule != null) {
              triggerCapsule.processTrigger();
            }
          } catch (InterruptedException ex) {
            logger.info(ex.getMessage());
            Thread.currentThread().interrupt();
          } catch (Throwable throwable) {
            logger.error("unknown throwable happened in process capsule loop", throwable);
          }
        }
      };

  public ExecutiveStore getExecutiveStore() {
    return chainBaseManager.getExecutiveStore();
  }

  public boolean needToUpdateAsset() {
    return getDynamicPropertiesStore().getTokenUpdateDone() == 0L;
  }

  public boolean needToMoveAbi() {
    return getDynamicPropertiesStore().getAbiMoveDone() == 0L;
  }

  public DynamicPropertiesStore getDynamicPropertiesStore() {
    return chainBaseManager.getDynamicPropertiesStore();
  }

  public DelegationStore getDelegationStore() {
    return chainBaseManager.getDelegationStore();
  }

  public IncrementalMerkleTreeStore getMerkleTreeStore() {
    return chainBaseManager.getMerkleTreeStore();
  }

  public ExecutiveScheduleStore getExecutiveScheduleStore() {
    return chainBaseManager.getExecutiveScheduleStore();
  }

  public DelegatedResourceStore getDelegatedResourceStore() {
    return chainBaseManager.getDelegatedResourceStore();
  }

  public DelegatedResourceAccountIndexStore getDelegatedResourceAccountIndexStore() {
    return chainBaseManager.getDelegatedResourceAccountIndexStore();
  }

  public CodeStore getCodeStore() {
    return chainBaseManager.getCodeStore();
  }

  public ContractStore getContractStore() {
    return chainBaseManager.getContractStore();
  }

  public VotesStore getVotesStore() {
    return chainBaseManager.getVotesStore();
  }

  public ProposalStore getProposalStore() {
    return chainBaseManager.getProposalStore();
  }

  public ExchangeStore getExchangeStore() {
    return chainBaseManager.getExchangeStore();
  }

  public ExchangeV2Store getExchangeV2Store() {
    return chainBaseManager.getExchangeV2Store();
  }

  public StorageRowStore getStorageRowStore() {
    return chainBaseManager.getStorageRowStore();
  }

  public BlockIndexStore getBlockIndexStore() {
    return chainBaseManager.getBlockIndexStore();
  }

  public BlockingQueue<TransactionCapsule> getPendingTransactions() {
    return this.pendingTransactions;
  }

  public List<TransactionCapsule> getPoppedTransactions() {
    return this.poppedTransactions;
  }

  public BlockingQueue<TransactionCapsule> getRePushTransactions() {
    return rePushTransactions;
  }

  public void stopRePushThread() {
    isRunRePushThread = false;
  }

  public void stopRePushTriggerThread() {
    isRunTriggerCapsuleProcessThread = false;
  }

  private Comparator downComparator = (Comparator<TransactionCapsule>) (o1, o2) -> Long
      .compare(o2.getOrder(), o1.getOrder());

  @PostConstruct
  public void init() {
    Message.setDynamicPropertiesStore(this.getDynamicPropertiesStore());
    mortgageService
        .initStore(chainBaseManager.getExecutiveStore(), chainBaseManager.getDelegationStore(),
            chainBaseManager.getDynamicPropertiesStore(), chainBaseManager.getAccountStore());
    accountStateCallBack.setChainBaseManager(chainBaseManager);
    trieService.setChainBaseManager(chainBaseManager);
    revokingStore.disable();
    revokingStore.check();
    this.setProposalController(ProposalController.createInstance(this));
    this.setMerkleContainer(
        merkleContainer.createInstance(chainBaseManager.getMerkleTreeStore(),
            chainBaseManager.getMerkleTreeIndexStore()));
    if (Args.getInstance().isOpenTransactionSort()) {
      this.pendingTransactions = new PriorityBlockingQueue(2000, downComparator);
      this.rePushTransactions = new PriorityBlockingQueue<>(2000, downComparator);
    } else {
      this.pendingTransactions = new LinkedBlockingQueue<>();
      this.rePushTransactions = new LinkedBlockingQueue<>();
    }
    this.triggerCapsuleQueue = new LinkedBlockingQueue<>();
    chainBaseManager.setMerkleContainer(getMerkleContainer());
    chainBaseManager.setMortgageService(mortgageService);
    chainBaseManager.init();
    this.initGenesis();
    try {
      this.khaosDb.start(chainBaseManager.getBlockById(
          getDynamicPropertiesStore().getLatestBlockHeaderHash()));
    } catch (ItemNotFoundException e) {
      logger.error(
          "Can not find Dynamic highest block from DB! \nnumber={} \nhash={}",
          getDynamicPropertiesStore().getLatestBlockHeaderNumber(),
          getDynamicPropertiesStore().getLatestBlockHeaderHash());
      logger.error(
          "Please delete database directory({}) and restart",
          Args.getInstance().getOutputDirectory());
      System.exit(1);
    } catch (BadItemException e) {
      logger.error("DB data broken! {}", e);
      logger.error(
          "Please delete database directory({}) and restart",
          Args.getInstance().getOutputDirectory());
      System.exit(1);
    }
    getChainBaseManager().getForkController().init(this.chainBaseManager);

    if (Args.getInstance().isNeedToUpdateAsset() && needToUpdateAsset()) {
      new AssetUpdateHelper(chainBaseManager).doWork();
    }

    if (needToMoveAbi()) {
      new MoveAbiHelper(chainBaseManager).doWork();
    }


    //for test only
    chainBaseManager.getDynamicPropertiesStore().updateDynamicStoreByConfig();

    // initCacheTxs();
    revokingStore.enable();
    validateSignService = Executors
        .newFixedThreadPool(Args.getInstance().getValidateSignThreadNum());
    Thread rePushThread = new Thread(rePushLoop);
    rePushThread.start();
    // add contract event listener for subscribing
    if (Args.getInstance().isEventSubscribe()) {
      startEventSubscribing();
      Thread triggerCapsuleProcessThread = new Thread(triggerCapsuleProcessLoop);
      triggerCapsuleProcessThread.start();
    }

    //initStoreFactory
    prepareStoreFactory();
    //initActuatorCreator
    ActuatorCreator.init();
    TransactionRegister.registerActuator();
  }

  /**
   * init genesis block.
   */
  public void initGenesis() {
    chainBaseManager.initGenesis();
    BlockCapsule genesisBlock = chainBaseManager.getGenesisBlock();

    if (chainBaseManager.containBlock(genesisBlock.getBlockId())) {
      Args.getInstance().setChainId(genesisBlock.getBlockId().toString());
    } else {
      if (chainBaseManager.hasBlocks()) {
        logger.error(
            "genesis block modify, please delete database directory({}) and restart",
            Args.getInstance().getOutputDirectory());
        System.exit(1);
      } else {
        logger.info("create genesis block");
        Args.getInstance().setChainId(genesisBlock.getBlockId().toString());

        chainBaseManager.getBlockStore().put(genesisBlock.getBlockId().getBytes(), genesisBlock);
        chainBaseManager.getBlockIndexStore().put(genesisBlock.getBlockId());

        logger.info(SAVE_BLOCK + genesisBlock);
        // init Dynamic Properties Store
        chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(0);
        chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderHash(
            genesisBlock.getBlockId().getByteString());
        chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(
            genesisBlock.getTimeStamp());
        this.initAccount();
        this.initExecutive();
        this.khaosDb.start(genesisBlock);
        this.updateRecentBlock(genesisBlock);
        initAccountHistoryBalance();
      }
    }
  }

  /**
   * save account into database.
   */
  public void initAccount() {
    final CommonParameter parameter = CommonParameter.getInstance();
    final GenesisBlock genesisBlockArg = parameter.getGenesisBlock();
    genesisBlockArg
        .getAssets()
        .forEach(
            account -> {
              account.setAccountType("Normal"); // to be set in conf
              final AccountCapsule accountCapsule =
                  new AccountCapsule(
                      account.getAccountName(),
                      ByteString.copyFrom(account.getAddress()),
                      account.getAccountType(),
                      account.getBalance());
              chainBaseManager.getAccountStore().put(account.getAddress(), accountCapsule);
              chainBaseManager.getAccountIdIndexStore().put(accountCapsule);
              chainBaseManager.getAccountIndexStore().put(accountCapsule);
            });
  }

  public void initAccountHistoryBalance() {
    BlockCapsule genesis = chainBaseManager.getGenesisBlock();
    BlockBalanceTraceCapsule genesisBlockBalanceTraceCapsule =
        new BlockBalanceTraceCapsule(genesis);
    List<TransactionCapsule> transactionCapsules = genesis.getTransactions();
    for (TransactionCapsule transactionCapsule : transactionCapsules) {
      BalanceContract.TransferContract transferContract = transactionCapsule.getTransferContract();
      BalanceContract.TransactionBalanceTrace.Operation operation =
          BalanceContract.TransactionBalanceTrace.Operation.newBuilder()
              .setOperationIdentifier(0)
              .setAddress(transferContract.getToAddress())
              .setAmount(transferContract.getAmount())
              .build();

      BalanceContract.TransactionBalanceTrace transactionBalanceTrace =
          BalanceContract.TransactionBalanceTrace.newBuilder()
              .setTransactionIdentifier(transactionCapsule.getTransactionId().getByteString())
              .setType(TransferContract.name())
              .setStatus(SUCCESS.name())
              .addOperation(operation)
              .build();
      genesisBlockBalanceTraceCapsule.addTransactionBalanceTrace(transactionBalanceTrace);

      chainBaseManager.getAccountTraceStore().recordBalanceWithBlock(
          transferContract.getToAddress().toByteArray(), 0, transferContract.getAmount());
    }

    chainBaseManager.getBalanceTraceStore()
        .put(Longs.toByteArray(0), genesisBlockBalanceTraceCapsule);
  }

  /**
   * save executives into database.
   */
  private void initExecutive() {
    final CommonParameter commonParameter = Args.getInstance();
    final GenesisBlock genesisBlockArg = commonParameter.getGenesisBlock();
    genesisBlockArg
        .getExecutives()
        .forEach(
            key -> {
              byte[] keyAddress = key.getAddress();
              ByteString address = ByteString.copyFrom(keyAddress);

              final AccountCapsule accountCapsule;
              if (!chainBaseManager.getAccountStore().has(keyAddress)) {
                accountCapsule = new AccountCapsule(ByteString.EMPTY,
                    address, AccountType.AssetIssue, 0L);
              } else {
                accountCapsule = chainBaseManager.getAccountStore().getUnchecked(keyAddress);
              }
              accountCapsule.setIsExecutive(true);
              chainBaseManager.getAccountStore().put(keyAddress, accountCapsule);

              final ExecutiveCapsule executiveCapsule =
                  new ExecutiveCapsule(address, key.getVoteCount(), key.getUrl());
              executiveCapsule.setIsJobs(true);
              chainBaseManager.getExecutiveStore().put(keyAddress, executiveCapsule);
            });
  }

  public void initCacheTxs() {
    logger.info("begin to init txs cache.");
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (dbVersion != 2) {
      return;
    }
    long start = System.currentTimeMillis();
    long headNum = chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber();
    logger.info("current headNum is: {}", headNum);
    long recentBlockCount = chainBaseManager.getRecentBlockStore().size();
    ListeningExecutorService service = MoreExecutors
        .listeningDecorator(Executors.newFixedThreadPool(50));
    List<ListenableFuture<?>> futures = new ArrayList<>();
    AtomicLong blockCount = new AtomicLong(0);
    AtomicLong emptyBlockCount = new AtomicLong(0);
    LongStream.rangeClosed(headNum - recentBlockCount + 1, headNum).forEach(
        blockNum -> futures.add(service.submit(() -> {
          try {
            blockCount.incrementAndGet();
            if (chainBaseManager.getBlockByNum(blockNum).getTransactions().isEmpty()) {
              emptyBlockCount.incrementAndGet();
              // transactions is null, return
              return;
            }
            chainBaseManager.getBlockByNum(blockNum).getTransactions().stream()
                .map(tc -> tc.getTransactionId().getBytes())
                .map(bytes -> Maps.immutableEntry(bytes, Longs.toByteArray(blockNum)))
                .forEach(e -> transactionCache
                    .put(e.getKey(), new BytesCapsule(e.getValue())));
          } catch (ItemNotFoundException e) {
            if (!CommonParameter.getInstance().isLiteFullNode) {
              logger.warn("block not found. num: {}", blockNum);
            }
          } catch (BadItemException e) {
            throw new IllegalStateException("init txs cache error.", e);
          }
        })));

    ListenableFuture<?> future = Futures.allAsList(futures);
    try {
      future.get();
      service.shutdown();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.info(e.getMessage());
    }

    logger.info("end to init txs cache. stb ids:{}, block count:{}, empty block count:{}, cost:{}",
        transactionCache.size(),
        blockCount.get(),
        emptyBlockCount.get(),
        System.currentTimeMillis() - start
    );
  }

  public AccountStore getAccountStore() {
    return chainBaseManager.getAccountStore();
  }

  public AccountAssetStore getAccountAssetStore() {
    return chainBaseManager.getAccountAssetStore();
  }

  public AccountIndexStore getAccountIndexStore() {
    return chainBaseManager.getAccountIndexStore();
  }

  void validateTapos(TransactionCapsule transactionCapsule) throws TaposException {
    byte[] refBlockHash = transactionCapsule.getInstance()
        .getRawData().getRefBlockHash().toByteArray();
    byte[] refBlockNumBytes = transactionCapsule.getInstance()
        .getRawData().getRefBlockBytes().toByteArray();
    try {
      byte[] blockHash = chainBaseManager.getRecentBlockStore().get(refBlockNumBytes).getData();
      if (!Arrays.equals(blockHash, refBlockHash)) {
        String str = String.format(
            "Tapos failed, different block hash, %s, %s , recent block %s, "
                + "solid block %s head block %s",
            ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
            Hex.toHexString(blockHash),
            chainBaseManager.getSolidBlockId().getString(),
            chainBaseManager.getHeadBlockId().getString()).toString();
        logger.info(str);
        throw new TaposException(str);
      }
    } catch (ItemNotFoundException e) {
      String str = String
          .format("Tapos failed, block not found, ref block %s, %s , solid block %s head block %s",
              ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
              chainBaseManager.getSolidBlockId().getString(),
              chainBaseManager.getHeadBlockId().getString()).toString();
      logger.info(str);
      throw new TaposException(str);
    }
  }

  void validateCommon(TransactionCapsule transactionCapsule)
      throws TransactionExpirationException, TooBigTransactionException {
    if (transactionCapsule.getData().length > Constant.TRANSACTION_MAX_BYTE_SIZE) {
      throw new TooBigTransactionException(
          "too big transaction, the size is " + transactionCapsule.getData().length + " bytes");
    }
    long transactionExpiration = transactionCapsule.getExpiration();
    long headBlockTime = chainBaseManager.getHeadBlockTimeStamp();
    if (transactionExpiration <= headBlockTime
        || transactionExpiration > headBlockTime + Constant.MAXIMUM_TIME_UNTIL_EXPIRATION) {
      throw new TransactionExpirationException(
          "transaction expiration, transaction expiration time is " + transactionExpiration
              + ", but headBlockTime is " + headBlockTime);
    }
  }

  void validateDup(TransactionCapsule transactionCapsule) throws DupTransactionException {
    if (containsTransaction(transactionCapsule)) {
      logger.debug(ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes()));
      throw new DupTransactionException("dup trans");
    }
  }

  private boolean containsTransaction(TransactionCapsule transactionCapsule) {
    return containsTransaction(transactionCapsule.getTransactionId().getBytes());
  }


  private boolean containsTransaction(byte[] transactionId) {
    if (transactionCache != null) {
      return transactionCache.has(transactionId);
    }

    return chainBaseManager.getTransactionStore()
        .has(transactionId);
  }

  /**
   * push transaction into pending.
   */
  public boolean pushTransaction(final TransactionCapsule stb)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, DupTransactionException, TaposException,
      TooBigTransactionException, TransactionExpirationException,
      ReceiptCheckErrException, VMIllegalException, TooBigTransactionResultException {

    if (isShieldedTransaction(stb.getInstance()) && !Args.getInstance()
        .isFullNodeAllowShieldedTransactionArgs()) {
      return true;
    }

    pushTransactionQueue.add(stb);

    try {
      if (!stb.validateSignature(chainBaseManager.getAccountStore(),
          chainBaseManager.getDynamicPropertiesStore())) {
        throw new ValidateSignatureException("trans sig validate failed");
      }

      synchronized (this) {
        if (isShieldedTransaction(stb.getInstance())
            && shieldedTransInPendingCounts.get() >= shieldedTransInPendingMaxCounts) {
          return false;
        }
        if (!session.valid()) {
          session.setValue(revokingStore.buildSession());
        }

        try (ISession tmpSession = revokingStore.buildSession()) {
          processTransaction(stb, null);
          stb.setStbTrace(null);
          pendingTransactions.add(stb);
          tmpSession.merge();
        }
        if (isShieldedTransaction(stb.getInstance())) {
          shieldedTransInPendingCounts.incrementAndGet();
        }
      }
    } finally {
      pushTransactionQueue.remove(stb);
    }
    return true;
  }

  public void consumeMultiSignFee(TransactionCapsule stb, TransactionTrace trace)
      throws AccountResourceInsufficientException {
    if (stb.getInstance().getSignatureCount() > 1) {
      long fee = getDynamicPropertiesStore().getMultiSignFee();

      List<Contract> contracts = stb.getInstance().getRawData().getContractList();
      for (Contract contract : contracts) {
        byte[] address = TransactionCapsule.getOwner(contract);
        AccountCapsule accountCapsule = getAccountStore().get(address);
        try {
          if (accountCapsule != null) {
            adjustBalance(getAccountStore(), accountCapsule, -fee);

            if (getDynamicPropertiesStore().supportBlackHoleOptimization()) {
              getDynamicPropertiesStore().burnStb(fee);
            } else {
              adjustBalance(getAccountStore(), this.getAccountStore().getUnit(), +fee);
            }
          }
        } catch (BalanceInsufficientException e) {
          throw new AccountResourceInsufficientException(
              "Account Insufficient balance[" + fee + "] to MultiSign");
        }
      }

      trace.getReceipt().setMultiSignFee(fee);
    }
  }

  public void consumeBandwidth(TransactionCapsule stb, TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException,
      TooBigTransactionResultException {
    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    processor.consume(stb, trace);
  }


  /**
   * when switch fork need erase blocks on fork branch.
   */
  public synchronized void eraseBlock() {
    session.reset();
    try {
      BlockCapsule oldHeadBlock = chainBaseManager.getBlockById(
          getDynamicPropertiesStore().getLatestBlockHeaderHash());
      logger.info("start to erase block:" + oldHeadBlock);
      khaosDb.pop();
      revokingStore.fastPop();
      logger.info("end to erase block:" + oldHeadBlock);
      poppedTransactions.addAll(oldHeadBlock.getTransactions());

    } catch (ItemNotFoundException | BadItemException e) {
      logger.warn(e.getMessage(), e);
    }
  }

  public void pushVerifiedBlock(BlockCapsule block) throws ContractValidateException,
      ContractExeException, ValidateSignatureException, AccountResourceInsufficientException,
      TransactionExpirationException, TooBigTransactionException, DupTransactionException,
      TaposException, ValidateScheduleException, ReceiptCheckErrException,
      VMIllegalException, TooBigTransactionResultException, UnLinkedBlockException,
      NonCommonBlockException, BadNumberBlockException, BadBlockException, ZksnarkException {
    block.generatedByMyself = true;
    long start = System.currentTimeMillis();
    pushBlock(block);
    logger.info("push block cost:{}ms, blockNum:{}, blockHash:{}, stb count:{}",
        System.currentTimeMillis() - start,
        block.getNum(),
        block.getBlockId(),
        block.getTransactions().size());
  }

  private void applyBlock(BlockCapsule block) throws ContractValidateException,
          ContractExeException, ValidateSignatureException, AccountResourceInsufficientException,
          TransactionExpirationException, TooBigTransactionException, DupTransactionException,
          TaposException, ValidateScheduleException, ReceiptCheckErrException,
          VMIllegalException, TooBigTransactionResultException,
          ZksnarkException, BadBlockException {
    applyBlock(block, block.getTransactions());
  }

  private void applyBlock(BlockCapsule block, List<TransactionCapsule> txs)
          throws ContractValidateException, ContractExeException, ValidateSignatureException,
          AccountResourceInsufficientException, TransactionExpirationException,
          TooBigTransactionException,DupTransactionException, TaposException,
          ValidateScheduleException, ReceiptCheckErrException, VMIllegalException,
          TooBigTransactionResultException, ZksnarkException, BadBlockException {
    processBlock(block, txs);
    chainBaseManager.getBlockStore().put(block.getBlockId().getBytes(), block);
    chainBaseManager.getBlockIndexStore().put(block.getBlockId());
    if (block.getTransactions().size() != 0) {
      chainBaseManager.getTransactionRetStore()
          .put(ByteArray.fromLong(block.getNum()), block.getResult());
    }

    updateFork(block);
    if (System.currentTimeMillis() - block.getTimeStamp() >= 60_000) {
      revokingStore.setMaxFlushCount(SnapshotManager.DEFAULT_MAX_FLUSH_COUNT);
    } else {
      revokingStore.setMaxFlushCount(SnapshotManager.DEFAULT_MIN_FLUSH_COUNT);
    }
  }

  private void switchFork(BlockCapsule newHead)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      ValidateScheduleException, AccountResourceInsufficientException, TaposException,
      TooBigTransactionException, TooBigTransactionResultException, DupTransactionException,
      TransactionExpirationException, NonCommonBlockException, ReceiptCheckErrException,
      VMIllegalException, ZksnarkException, BadBlockException {

    MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_FORK_COUNT);

    Pair<LinkedList<KhaosBlock>, LinkedList<KhaosBlock>> binaryTree;
    try {
      binaryTree =
          khaosDb.getBranch(
              newHead.getBlockId(), getDynamicPropertiesStore().getLatestBlockHeaderHash());
    } catch (NonCommonBlockException e) {
      MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_FAIL_FORK_COUNT);
      logger.info(
          "this is not the most recent common ancestor, "
              + "need to remove all blocks in the fork chain.");
      BlockCapsule tmp = newHead;
      while (tmp != null) {
        khaosDb.removeBlk(tmp.getBlockId());
        tmp = khaosDb.getBlock(tmp.getParentHash());
      }

      throw e;
    }

    if (CollectionUtils.isNotEmpty(binaryTree.getValue())) {
      while (!getDynamicPropertiesStore()
          .getLatestBlockHeaderHash()
          .equals(binaryTree.getValue().peekLast().getParentHash())) {
        reOrgContractTrigger();
        eraseBlock();
      }
    }

    if (CollectionUtils.isNotEmpty(binaryTree.getKey())) {
      List<KhaosBlock> first = new ArrayList<>(binaryTree.getKey());
      Collections.reverse(first);
      for (KhaosBlock item : first) {
        Exception exception = null;
        // todo  process the exception carefully later
        try (ISession tmpSession = revokingStore.buildSession()) {
          applyBlock(item.getBlk().setSwitch(true));
          tmpSession.commit();
        } catch (AccountResourceInsufficientException
            | ValidateSignatureException
            | ContractValidateException
            | ContractExeException
            | TaposException
            | DupTransactionException
            | TransactionExpirationException
            | ReceiptCheckErrException
            | TooBigTransactionException
            | TooBigTransactionResultException
            | ValidateScheduleException
            | VMIllegalException
            | ZksnarkException
            | BadBlockException e) {
          logger.warn(e.getMessage(), e);
          exception = e;
          throw e;
        } finally {
          if (exception != null) {
            MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_FAIL_FORK_COUNT);
            logger.warn("switch back because exception thrown while switching forks. " + exception
                    .getMessage(),
                exception);
            first.forEach(khaosBlock -> khaosDb.removeBlk(khaosBlock.getBlk().getBlockId()));
            khaosDb.setHead(binaryTree.getValue().peekFirst());

            while (!getDynamicPropertiesStore()
                .getLatestBlockHeaderHash()
                .equals(binaryTree.getValue().peekLast().getParentHash())) {
              eraseBlock();
            }

            List<KhaosBlock> second = new ArrayList<>(binaryTree.getValue());
            Collections.reverse(second);
            for (KhaosBlock khaosBlock : second) {
              // todo  process the exception carefully later
              try (ISession tmpSession = revokingStore.buildSession()) {
                applyBlock(khaosBlock.getBlk().setSwitch(true));
                tmpSession.commit();
              } catch (AccountResourceInsufficientException
                  | ValidateSignatureException
                  | ContractValidateException
                  | ContractExeException
                  | TaposException
                  | DupTransactionException
                  | TransactionExpirationException
                  | TooBigTransactionException
                  | ValidateScheduleException
                  | ZksnarkException e) {
                logger.warn(e.getMessage(), e);
              }
            }
          }
        }
      }
    }

  }

  public List<TransactionCapsule> getVerifyTxs(BlockCapsule block) {

    if (pendingTransactions.size() == 0) {
      return block.getTransactions();
    }

    List<TransactionCapsule> txs = new ArrayList<>();
    Set<String> txIds = new HashSet<>();
    Set<String> multiAddresses = new HashSet<>();

    pendingTransactions.forEach(capsule -> {
      String txId = Hex.toHexString(capsule.getTransactionId().getBytes());
      if (isMultiSignTransaction(capsule.getInstance())) {
        Contract contract = capsule.getInstance().getRawData().getContract(0);
        String address = Hex.toHexString(TransactionCapsule.getOwner(contract));
        multiAddresses.add(address);
      } else {
        txIds.add(txId);
      }
    });

    block.getTransactions().forEach(capsule -> {
      Contract contract = capsule.getInstance().getRawData().getContract(0);
      String address = Hex.toHexString(TransactionCapsule.getOwner(contract));
      String txId = Hex.toHexString(capsule.getTransactionId().getBytes());
      if (multiAddresses.contains(address) || !txIds.contains(txId)) {
        txs.add(capsule);
      } else {
        capsule.setVerified(true);
      }
    });

    return txs;
  }

  /**
   * save a block.
   */
  public synchronized void pushBlock(final BlockCapsule block)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      UnLinkedBlockException, ValidateScheduleException, AccountResourceInsufficientException,
      TaposException, TooBigTransactionException, TooBigTransactionResultException,
      DupTransactionException, TransactionExpirationException,
      BadNumberBlockException, BadBlockException, NonCommonBlockException,
      ReceiptCheckErrException, VMIllegalException, ZksnarkException {
    long start = System.currentTimeMillis();
    List<TransactionCapsule> txs = getVerifyTxs(block);
    logger.info("Block num: {}, re-push-size: {}, pending-size: {}, "
                    + "block-tx-size: {}, verify-tx-size: {}",
            block.getNum(), rePushTransactions.size(), pendingTransactions.size(),
            block.getTransactions().size(), txs.size());
    try (PendingManager pm = new PendingManager(this)) {

      if (!block.generatedByMyself) {
        if (!block.validateSignature(chainBaseManager.getDynamicPropertiesStore(),
            chainBaseManager.getAccountStore())) {
          logger.warn("The signature is not validated.");
          throw new BadBlockException("The signature is not validated");
        }

        if (!block.calcMerkleRoot().equals(block.getMerkleRoot())) {
          logger.warn(
              "The merkle root doesn't match, Calc result is "
                  + block.calcMerkleRoot()
                  + " , the headers is "
                  + block.getMerkleRoot());
          throw new BadBlockException("The merkle hash is not validated");
        }
        consensus.receiveBlock(block);
      }

      if (block.getTransactions().stream().filter(tran -> isShieldedTransaction(tran.getInstance()))
          .count() > SHIELDED_TRANS_IN_BLOCK_COUNTS) {
        throw new BadBlockException(
            "shielded transaction count > " + SHIELDED_TRANS_IN_BLOCK_COUNTS);
      }

      BlockCapsule newBlock;
      try {
        newBlock = this.khaosDb.push(block);
      } catch (UnLinkedBlockException e) {
        logger.error(
            "latestBlockHeaderHash:{}, latestBlockHeaderNumber:{}, latestSolidifiedBlockNum:{}",
            getDynamicPropertiesStore().getLatestBlockHeaderHash(),
            getDynamicPropertiesStore().getLatestBlockHeaderNumber(),
            getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
        throw e;
      }

      // DB don't need lower block
      if (getDynamicPropertiesStore().getLatestBlockHeaderHash() == null) {
        if (newBlock.getNum() != 0) {
          return;
        }
      } else {
        if (newBlock.getNum() <= getDynamicPropertiesStore().getLatestBlockHeaderNumber()) {
          return;
        }

        // switch fork
        if (!newBlock
            .getParentHash()
            .equals(getDynamicPropertiesStore().getLatestBlockHeaderHash())) {
          logger.warn(
              "switch fork! new head num = {}, block id = {}",
              newBlock.getNum(),
              newBlock.getBlockId());

          logger.warn(
              "******** before switchFork ******* push block: "
                  + block.toString()
                  + ", new block:"
                  + newBlock.toString()
                  + ", dynamic head num: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
                  + ", dynamic head hash: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderHash()
                  + ", dynamic head timestamp: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp()
                  + ", khaosDb head: "
                  + khaosDb.getHead()
                  + ", khaosDb miniStore size: "
                  + khaosDb.getMiniStore().size()
                  + ", khaosDb unlinkMiniStore size: "
                  + khaosDb.getMiniUnlinkedStore().size());

          switchFork(newBlock);
          logger.info(SAVE_BLOCK + newBlock);

          logger.warn(
              "******** after switchFork ******* push block: "
                  + block.toString()
                  + ", new block:"
                  + newBlock.toString()
                  + ", dynamic head num: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
                  + ", dynamic head hash: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderHash()
                  + ", dynamic head timestamp: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp()
                  + ", khaosDb head: "
                  + khaosDb.getHead()
                  + ", khaosDb miniStore size: "
                  + khaosDb.getMiniStore().size()
                  + ", khaosDb unlinkMiniStore size: "
                  + khaosDb.getMiniUnlinkedStore().size());

          return;
        }
        try (ISession tmpSession = revokingStore.buildSession()) {

          applyBlock(newBlock, txs);
          tmpSession.commit();
          // if event subscribe is enabled, post solidity trigger to queue
          postSolidityTrigger(getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
          // if event subscribe is enabled, post block trigger to queue
          postBlockTrigger(newBlock);
        } catch (Throwable throwable) {
          logger.error(throwable.getMessage(), throwable);
          khaosDb.removeBlk(block.getBlockId());
          throw throwable;
        }
      }
      logger.info(SAVE_BLOCK + newBlock);
    }
    //clear ownerAddressSet
    if (CollectionUtils.isNotEmpty(ownerAddressSet)) {
      Set<String> result = new HashSet<>();
      for (TransactionCapsule transactionCapsule : rePushTransactions) {
        filterOwnerAddress(transactionCapsule, result);
      }
      for (TransactionCapsule transactionCapsule : pushTransactionQueue) {
        filterOwnerAddress(transactionCapsule, result);
      }
      ownerAddressSet.clear();
      ownerAddressSet.addAll(result);
    }

    MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_BLOCK_PROCESS_TIME,
        System.currentTimeMillis() - start);

    logger.info("pushBlock block number:{}, cost/txs:{}/{}",
        block.getNum(),
        System.currentTimeMillis() - start,
        block.getTransactions().size());
  }

  public void updateDynamicProperties(BlockCapsule block) {

    chainBaseManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderHash(block.getBlockId().getByteString());

    chainBaseManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderNumber(block.getNum());
    chainBaseManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderTimestamp(block.getTimeStamp());
    revokingStore.setMaxSize((int) (
        chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
            - chainBaseManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum()
            + 1));
    khaosDb.setMaxSize((int)
        (chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
            - chainBaseManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum()
            + 1));
  }

  /**
   * Get the fork branch.
   */
  public LinkedList<BlockId> getBlockChainHashesOnFork(final BlockId forkBlockHash)
      throws NonCommonBlockException {
    final Pair<LinkedList<KhaosBlock>, LinkedList<KhaosBlock>> branch =
        this.khaosDb.getBranch(
            getDynamicPropertiesStore().getLatestBlockHeaderHash(), forkBlockHash);

    LinkedList<KhaosBlock> blockCapsules = branch.getValue();

    if (blockCapsules.isEmpty()) {
      logger.info("empty branch {}", forkBlockHash);
      return Lists.newLinkedList();
    }

    LinkedList<BlockId> result = blockCapsules.stream()
        .map(KhaosBlock::getBlk)
        .map(BlockCapsule::getBlockId)
        .collect(Collectors.toCollection(LinkedList::new));

    result.add(blockCapsules.peekLast().getBlk().getParentBlockId());

    return result;
  }

  /**
   * Process transaction.
   */
  public TransactionInfo processTransaction(final TransactionCapsule stbCap, BlockCapsule blockCap)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, TransactionExpirationException,
      TooBigTransactionException, TooBigTransactionResultException,
      DupTransactionException, TaposException, ReceiptCheckErrException, VMIllegalException {
    if (stbCap == null) {
      return null;
    }

    if (Objects.nonNull(blockCap)) {
      chainBaseManager.getBalanceTraceStore().initCurrentTransactionBalanceTrace(stbCap);
    }

    validateTapos(stbCap);
    validateCommon(stbCap);

    if (stbCap.getInstance().getRawData().getContractList().size() != 1) {
      throw new ContractSizeNotEqualToOneException(
          "act size should be exactly 1, this is extend feature");
    }

    validateDup(stbCap);

    if (!stbCap.validateSignature(chainBaseManager.getAccountStore(),
        chainBaseManager.getDynamicPropertiesStore())) {
      throw new ValidateSignatureException("transaction signature validate failed");
    }

    TransactionTrace trace = new TransactionTrace(stbCap, StoreFactory.getInstance(),
        new RuntimeImpl());
    stbCap.setStbTrace(trace);

    consumeBandwidth(stbCap, trace);
    consumeMultiSignFee(stbCap, trace);

    trace.init(blockCap, eventPluginLoaded);
    trace.checkIsConstant();
    trace.exec();

    if (Objects.nonNull(blockCap)) {
      trace.setResult();
      if (blockCap.hasExecutiveSignature()) {
        if (trace.checkNeedRetry()) {
          String txId = Hex.toHexString(stbCap.getTransactionId().getBytes());
          logger.info("Retry for tx id: {}", txId);
          trace.init(blockCap, eventPluginLoaded);
          trace.checkIsConstant();
          trace.exec();
          trace.setResult();
          logger.info("Retry result for tx id: {}, tx resultCode in receipt: {}",
              txId, trace.getReceipt().getResult());
        }
        trace.check();
      }
    }

    trace.finalization();
    if (Objects.nonNull(blockCap) && getDynamicPropertiesStore().supportVM()) {
      stbCap.setResult(trace.getTransactionContext());
    }
    chainBaseManager.getTransactionStore().put(stbCap.getTransactionId().getBytes(), stbCap);

    Optional.ofNullable(transactionCache)
        .ifPresent(t -> t.put(stbCap.getTransactionId().getBytes(),
            new BytesCapsule(ByteArray.fromLong(stbCap.getBlockNum()))));

    TransactionInfoCapsule transactionInfo = TransactionUtil
        .buildTransactionInfoInstance(stbCap, blockCap, trace);

    // if event subscribe is enabled, post contract triggers to queue
    postContractTrigger(trace, false);
    Contract contract = stbCap.getInstance().getRawData().getContract(0);
    if (isMultiSignTransaction(stbCap.getInstance())) {
      ownerAddressSet.add(ByteArray.toHexString(TransactionCapsule.getOwner(contract)));
    }

    if (Objects.nonNull(blockCap)) {
      chainBaseManager.getBalanceTraceStore()
          .updateCurrentTransactionStatus(
              trace.getRuntimeResult().getResultCode().name());
      chainBaseManager.getBalanceTraceStore().resetCurrentTransactionTrace();
    }
    //set the sort order
    stbCap.setOrder(transactionInfo.getFee());
    if (!eventPluginLoaded) {
      stbCap.setStbTrace(null);
    }
    return transactionInfo.getInstance();
  }

  /**
   * Generate a block.
   */
  public synchronized BlockCapsule generateBlock(Miner miner, long blockTime, long timeout) {

    long postponedStbCount = 0;

    BlockCapsule blockCapsule = new BlockCapsule(chainBaseManager.getHeadBlockNum() + 1,
        chainBaseManager.getHeadBlockId(),
        blockTime, miner.getExecutiveAddress());
    blockCapsule.generatedByMyself = true;
    session.reset();
    session.setValue(revokingStore.buildSession());

    accountStateCallBack.preExecute(blockCapsule);

    if (getDynamicPropertiesStore().getAllowMultiSign() == 1) {
      byte[] privateKeyAddress = miner.getPrivateKeyAddress().toByteArray();
      AccountCapsule executiveAccount = getAccountStore()
          .get(miner.getExecutiveAddress().toByteArray());
      if (!Arrays.equals(privateKeyAddress, executiveAccount.getExecutivePermissionAddress())) {
        logger.warn("Executive permission is wrong");
        return null;
      }
    }

    TransactionRetCapsule transactionRetCapsule = new TransactionRetCapsule(blockCapsule);

    Set<String> accountSet = new HashSet<>();
    AtomicInteger shieldedTransCounts = new AtomicInteger(0);
    while (pendingTransactions.size() > 0 || rePushTransactions.size() > 0) {
      boolean fromPending = false;
      TransactionCapsule stb;
      if (pendingTransactions.size() > 0) {
        stb = pendingTransactions.peek();
        if (Args.getInstance().isOpenTransactionSort()) {
          TransactionCapsule stbRepush = rePushTransactions.peek();
          if (stbRepush == null || stb.getOrder() >= stbRepush.getOrder()) {
            fromPending = true;
          } else {
            stb = rePushTransactions.poll();
          }
        } else {
          fromPending = true;
        }
      } else {
        stb = rePushTransactions.poll();
      }

      if (System.currentTimeMillis() > timeout) {
        logger.warn("Processing transaction time exceeds the producing time.");
        break;
      }

      // check the block size
      if ((blockCapsule.getInstance().getSerializedSize() + stb.getSerializedSize() + 3)
          > ChainConstant.BLOCK_SIZE) {
        postponedStbCount++;
        continue;
      }
      //shielded transaction
      if (isShieldedTransaction(stb.getInstance())
          && shieldedTransCounts.incrementAndGet() > SHIELDED_TRANS_IN_BLOCK_COUNTS) {
        continue;
      }
      //multi sign transaction
      Contract contract = stb.getInstance().getRawData().getContract(0);
      byte[] owner = TransactionCapsule.getOwner(contract);
      String ownerAddress = ByteArray.toHexString(owner);
      if (accountSet.contains(ownerAddress)) {
        continue;
      } else {
        if (isMultiSignTransaction(stb.getInstance())) {
          accountSet.add(ownerAddress);
        }
      }
      if (ownerAddressSet.contains(ownerAddress)) {
        stb.setVerified(false);
      }
      // apply transaction
      try (ISession tmpSession = revokingStore.buildSession()) {
        accountStateCallBack.preExeTrans();
        TransactionInfo result = processTransaction(stb, blockCapsule);
        accountStateCallBack.exeTransFinish();
        tmpSession.merge();
        blockCapsule.addTransaction(stb);
        if (Objects.nonNull(result)) {
          transactionRetCapsule.addTransactionInfo(result);
        }
        if (fromPending) {
          pendingTransactions.poll();
        }
      } catch (Exception e) {
        logger.error("Process stb {} failed when generating block: {}", stb.getTransactionId(),
            e.getMessage());
      }
    }

    accountStateCallBack.executeGenerateFinish();

    session.reset();

    logger.info("Generate block success, pendingCount: {}, rePushCount: {}, postponedCount: {}",
        pendingTransactions.size(), rePushTransactions.size(), postponedStbCount);

    blockCapsule.setMerkleRoot();
    blockCapsule.sign(miner.getPrivateKey());

    return blockCapsule;

  }

  private void filterOwnerAddress(TransactionCapsule transactionCapsule, Set<String> result) {
    Contract contract = transactionCapsule.getInstance().getRawData().getContract(0);
    byte[] owner = TransactionCapsule.getOwner(contract);
    String ownerAddress = ByteArray.toHexString(owner);
    if (ownerAddressSet.contains(ownerAddress)) {
      result.add(ownerAddress);
    }
  }

  private boolean isMultiSignTransaction(Transaction transaction) {
    Contract contract = transaction.getRawData().getContract(0);
    switch (contract.getType()) {
      case AccountPermissionUpdateContract: {
        return true;
      }
      default:
    }
    return false;
  }

  private boolean isShieldedTransaction(Transaction transaction) {
    Contract contract = transaction.getRawData().getContract(0);
    switch (contract.getType()) {
      case ShieldedTransferContract: {
        return true;
      }
      default:
        return false;
    }
  }

  public TransactionStore getTransactionStore() {
    return chainBaseManager.getTransactionStore();
  }

  public TransactionHistoryStore getTransactionHistoryStore() {
    return chainBaseManager.getTransactionHistoryStore();
  }

  public TransactionRetStore getTransactionRetStore() {
    return chainBaseManager.getTransactionRetStore();
  }

  public BlockStore getBlockStore() {
    return chainBaseManager.getBlockStore();
  }

  /**
   * process block.
   */
  private void processBlock(BlockCapsule block, List<TransactionCapsule> txs)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, TaposException, TooBigTransactionException,
      DupTransactionException, TransactionExpirationException, ValidateScheduleException,
      ReceiptCheckErrException, VMIllegalException, TooBigTransactionResultException,
      ZksnarkException, BadBlockException {
    // todo set revoking db max size.

    // checkExecutive
    if (!consensus.validBlock(block)) {
      throw new ValidateScheduleException("validateExecutiveSchedule error");
    }

    chainBaseManager.getBalanceTraceStore().initCurrentBlockBalanceTrace(block);

    //reset BlockUcrUsage
    chainBaseManager.getDynamicPropertiesStore().saveBlockUcrUsage(0);
    //parallel check sign
    if (!block.generatedByMyself) {
      try {
        preValidateTransactionSign(txs);
      } catch (InterruptedException e) {
        logger.error("parallel check sign interrupted exception! block info: {}", block, e);
        Thread.currentThread().interrupt();
      }
    }

    TransactionRetCapsule transactionRetCapsule =
        new TransactionRetCapsule(block);
    try {
      merkleContainer.resetCurrentMerkleTree();
      accountStateCallBack.preExecute(block);
      for (TransactionCapsule transactionCapsule : block.getTransactions()) {
        transactionCapsule.setBlockNum(block.getNum());
        if (block.generatedByMyself) {
          transactionCapsule.setVerified(true);
        }
        accountStateCallBack.preExeTrans();
        TransactionInfo result = processTransaction(transactionCapsule, block);
        accountStateCallBack.exeTransFinish();
        if (Objects.nonNull(result)) {
          transactionRetCapsule.addTransactionInfo(result);
        }
      }
      accountStateCallBack.executePushFinish();
    } finally {
      accountStateCallBack.exceptionFinish();
    }
    merkleContainer.saveCurrentMerkleTreeAsBestMerkleTree(block.getNum());
    block.setResult(transactionRetCapsule);
    if (getDynamicPropertiesStore().getAllowAdaptiveUcr() == 1) {
      UcrProcessor ucrProcessor = new UcrProcessor(
          chainBaseManager.getDynamicPropertiesStore(), chainBaseManager.getAccountStore());
      ucrProcessor.updateTotalUcrAverageUsage();
      ucrProcessor.updateAdaptiveTotalUcrLimit();
    }

    payReward(block);

    if (chainBaseManager.getDynamicPropertiesStore().getNextMaintenanceTime()
        <= block.getTimeStamp()) {
      proposalController.processProposals();
      chainBaseManager.getForkController().reset();
    }

    if (!consensus.applyBlock(block)) {
      throw new BadBlockException("consensus apply block failed");
    }

    updateTransHashCache(block);
    updateRecentBlock(block);
    updateDynamicProperties(block);

    chainBaseManager.getBalanceTraceStore().resetCurrentBlockTrace();
  }

  private void payReward(BlockCapsule block) {
    ExecutiveCapsule executiveCapsule =
        chainBaseManager.getExecutiveStore().getUnchecked(block.getInstance().getBlockHeader()
            .getRawData().getExecutiveAddress().toByteArray());
    if (getDynamicPropertiesStore().allowChangeDelegation()) {
      mortgageService.payBlockReward(executiveCapsule.getAddress().toByteArray(),
          getDynamicPropertiesStore().getExecutivePayPerBlock());
      mortgageService.payStandbyExecutive();

      if (chainBaseManager.getDynamicPropertiesStore().supportTransactionFeePool()) {
        long transactionFeeReward = Math
            .floorDiv(chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool(),
                Constant.TRANSACTION_FEE_POOL_PERIOD);
        mortgageService.payTransactionFeeReward(executiveCapsule.getAddress().toByteArray(),
            transactionFeeReward);
        chainBaseManager.getDynamicPropertiesStore().saveTransactionFeePool(
            chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool()
                - transactionFeeReward);
      }
    } else {
      byte[] executive = block.getExecutiveAddress().toByteArray();
      AccountCapsule account = getAccountStore().get(executive);
      account.setAllowance(account.getAllowance()
          + chainBaseManager.getDynamicPropertiesStore().getExecutivePayPerBlock());

      if (chainBaseManager.getDynamicPropertiesStore().supportTransactionFeePool()) {
        long transactionFeeReward = Math
            .floorDiv(chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool(),
                Constant.TRANSACTION_FEE_POOL_PERIOD);
        account.setAllowance(account.getAllowance() + transactionFeeReward);
        chainBaseManager.getDynamicPropertiesStore().saveTransactionFeePool(
            chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool()
                - transactionFeeReward);
      }

      getAccountStore().put(account.createDbKey(), account);
    }
  }

  private void postSolidityLogContractTrigger(Long blockNum, Long lastSolidityNum) {
    if (blockNum > lastSolidityNum) {
      return;
    }
    BlockingQueue contractLogTriggersQueue = Args.getSolidityContractLogTriggerMap()
        .get(blockNum);
    while (!contractLogTriggersQueue.isEmpty()) {
      ContractLogTrigger triggerCapsule = (ContractLogTrigger) contractLogTriggersQueue.poll();
      if (triggerCapsule == null) {
        break;
      }
      if (containsTransaction(ByteArray.fromHexString(triggerCapsule
          .getTransactionId()))) {
        triggerCapsule.setTriggerName(Trigger.SOLIDITYLOG_TRIGGER_NAME);
        EventPluginLoader.getInstance().postSolidityLogTrigger(triggerCapsule);
      }
    }
    Args.getSolidityContractLogTriggerMap().remove(blockNum);
  }

  private void postSolidityEventContractTrigger(Long blockNum, Long lastSolidityNum) {
    if (blockNum > lastSolidityNum) {
      return;
    }
    BlockingQueue contractEventTriggersQueue = Args.getSolidityContractEventTriggerMap()
        .get(blockNum);
    while (!contractEventTriggersQueue.isEmpty()) {
      ContractEventTrigger triggerCapsule = (ContractEventTrigger) contractEventTriggersQueue
          .poll();
      if (triggerCapsule == null) {
        break;
      }
      if (containsTransaction(ByteArray.fromHexString(triggerCapsule
          .getTransactionId()))) {
        triggerCapsule.setTriggerName(Trigger.SOLIDITYEVENT_TRIGGER_NAME);
        EventPluginLoader.getInstance().postSolidityEventTrigger(triggerCapsule);
      }
    }
    Args.getSolidityContractEventTriggerMap().remove(blockNum);
  }

  private void updateTransHashCache(BlockCapsule block) {
    for (TransactionCapsule transactionCapsule : block.getTransactions()) {
      this.transactionIdCache.put(transactionCapsule.getTransactionId(), true);
    }
  }

  public void updateRecentBlock(BlockCapsule block) {
    chainBaseManager.getRecentBlockStore().put(ByteArray.subArray(
        ByteArray.fromLong(block.getNum()), 6, 8),
        new BytesCapsule(ByteArray.subArray(block.getBlockId().getBytes(), 8, 16)));
  }

  public void updateFork(BlockCapsule block) {
    int blockVersion = block.getInstance().getBlockHeader().getRawData().getVersion();
    if (blockVersion > ChainConstant.BLOCK_VERSION) {
      logger.warn("newer block version found: " + blockVersion + ", YOU MUST UPGRADE java-stabila!");
    }
    chainBaseManager
        .getForkController().update(block);
  }

  public long getSyncBeginNumber() {
    logger.info("headNumber:"
        + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber());
    logger.info(
        "syncBeginNumber:"
            + (chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
            - revokingStore.size()));
    logger.info("solidBlockNumber:"
        + chainBaseManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
    return chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
        - revokingStore.size();
  }

  public AssetIssueStore getAssetIssueStore() {
    return chainBaseManager.getAssetIssueStore();
  }


  public AssetIssueV2Store getAssetIssueV2Store() {
    return chainBaseManager.getAssetIssueV2Store();
  }

  public AccountIdIndexStore getAccountIdIndexStore() {
    return chainBaseManager.getAccountIdIndexStore();
  }

  public NullifierStore getNullifierStore() {
    return chainBaseManager.getNullifierStore();
  }

  public void closeAllStore() {
    logger.info("******** begin to close db ********");
    chainBaseManager.closeAllStore();
    logger.info("******** end to close db ********");
  }

  public void closeOneStore(IStabilaChainBase database) {
    logger.info("******** begin to close " + database.getName() + " ********");
    try {
      database.close();
    } catch (Exception e) {
      logger.info("failed to close  " + database.getName() + ". " + e);
    } finally {
      logger.info("******** end to close " + database.getName() + " ********");
    }
  }

  public boolean isTooManyPending() {
    return getPendingTransactions().size() + getRePushTransactions().size()
        > maxTransactionPendingSize;
  }

  private void preValidateTransactionSign(List<TransactionCapsule> txs)
      throws InterruptedException, ValidateSignatureException {
    int transSize = txs.size();
    if (transSize <= 0) {
      return;
    }
    CountDownLatch countDownLatch = new CountDownLatch(transSize);
    List<Future<Boolean>> futures = new ArrayList<>(transSize);

    for (TransactionCapsule transaction : txs) {
      Future<Boolean> future = validateSignService
          .submit(new ValidateSignTask(transaction, countDownLatch, chainBaseManager));
      futures.add(future);
    }
    countDownLatch.await();

    for (Future<Boolean> future : futures) {
      try {
        future.get();
      } catch (ExecutionException e) {
        throw new ValidateSignatureException(e.getCause().getMessage());
      }
    }
  }

  public void rePush(TransactionCapsule tx) {
    if (containsTransaction(tx)) {
      return;
    }

    try {
      this.pushTransaction(tx);
    } catch (ValidateSignatureException | ContractValidateException | ContractExeException
        | AccountResourceInsufficientException | VMIllegalException e) {
      logger.debug(e.getMessage(), e);
    } catch (DupTransactionException e) {
      logger.debug("pending manager: dup trans", e);
    } catch (TaposException e) {
      logger.debug("pending manager: tapos exception", e);
    } catch (TooBigTransactionException e) {
      logger.debug("too big transaction");
    } catch (TransactionExpirationException e) {
      logger.debug("expiration transaction");
    } catch (ReceiptCheckErrException e) {
      logger.debug("outOfSlotTime transaction");
    } catch (TooBigTransactionResultException e) {
      logger.debug("too big transaction result");
    }
  }

  public long getHeadBlockNum() {
    return getDynamicPropertiesStore().getLatestBlockHeaderNumber();
  }

  public void setCursor(Chainbase.Cursor cursor) {
    if (cursor == Chainbase.Cursor.PBFT) {
      long headNum = getHeadBlockNum();
      long pbftNum = chainBaseManager.getCommonDataBase().getLatestPbftBlockNum();
      revokingStore.setCursor(cursor, headNum - pbftNum);
    } else {
      revokingStore.setCursor(cursor);
    }
  }

  public void resetCursor() {
    revokingStore.setCursor(Chainbase.Cursor.HEAD, 0L);
  }

  private void startEventSubscribing() {

    try {
      eventPluginLoaded = EventPluginLoader.getInstance()
          .start(Args.getInstance().getEventPluginConfig());

      if (!eventPluginLoaded) {
        logger.error("failed to load eventPlugin");
      }

      FilterQuery eventFilter = Args.getInstance().getEventFilter();
      if (!Objects.isNull(eventFilter)) {
        EventPluginLoader.getInstance().setFilterQuery(eventFilter);
      }

    } catch (Exception e) {
      logger.error("{}", e);
    }
  }

  private void postSolidityTrigger(final long latestSolidifiedBlockNumber) {
    if (eventPluginLoaded && EventPluginLoader.getInstance().isSolidityTriggerEnable()) {
      SolidityTriggerCapsule solidityTriggerCapsule
          = new SolidityTriggerCapsule(latestSolidifiedBlockNumber);
      boolean result = triggerCapsuleQueue.offer(solidityTriggerCapsule);
      if (!result) {
        logger.info("too many trigger, lost solidified trigger, "
            + "block number: {}", latestSolidifiedBlockNumber);
      }
    }
    if (eventPluginLoaded && EventPluginLoader.getInstance().isSolidityLogTriggerEnable()) {
      for (Long i : Args.getSolidityContractLogTriggerMap().keySet()) {
        postSolidityLogContractTrigger(i, latestSolidifiedBlockNumber);
      }
    }
    if (eventPluginLoaded && EventPluginLoader.getInstance().isSolidityEventTriggerEnable()) {
      for (Long i : Args.getSolidityContractEventTriggerMap().keySet()) {
        postSolidityEventContractTrigger(i, latestSolidifiedBlockNumber);
      }
    }
  }

  private void postBlockTrigger(final BlockCapsule newBlock) {
    if (eventPluginLoaded && EventPluginLoader.getInstance().isBlockLogTriggerEnable()) {
      BlockLogTriggerCapsule blockLogTriggerCapsule = new BlockLogTriggerCapsule(newBlock);
      blockLogTriggerCapsule.setLatestSolidifiedBlockNumber(getDynamicPropertiesStore()
          .getLatestSolidifiedBlockNum());
      if (!triggerCapsuleQueue.offer(blockLogTriggerCapsule)) {
        logger.info("too many triggers, block trigger lost: {}", newBlock.getBlockId());
      }
    }

    for (TransactionCapsule e : newBlock.getTransactions()) {
      postTransactionTrigger(e, newBlock);
    }
  }

  private void postTransactionTrigger(final TransactionCapsule stbCap,
      final BlockCapsule blockCap) {
    if (eventPluginLoaded && EventPluginLoader.getInstance().isTransactionLogTriggerEnable()) {
      TransactionLogTriggerCapsule stb = new TransactionLogTriggerCapsule(stbCap, blockCap);
      stb.setLatestSolidifiedBlockNumber(getDynamicPropertiesStore()
          .getLatestSolidifiedBlockNum());
      if (!triggerCapsuleQueue.offer(stb)) {
        logger.info("too many triggers, transaction trigger lost: {}", stbCap.getTransactionId());
      }
    }
  }

  private void reOrgContractTrigger() {
    if (eventPluginLoaded
        && (EventPluginLoader.getInstance().isContractEventTriggerEnable()
        || EventPluginLoader.getInstance().isContractLogTriggerEnable())) {
      logger.info("switchfork occurred, post reOrgContractTrigger");
      try {
        BlockCapsule oldHeadBlock = chainBaseManager.getBlockById(
            getDynamicPropertiesStore().getLatestBlockHeaderHash());
        for (TransactionCapsule stb : oldHeadBlock.getTransactions()) {
          postContractTrigger(stb.getStbTrace(), true);
        }
      } catch (BadItemException | ItemNotFoundException e) {
        logger.error("block header hash does not exist or is bad: {}",
            getDynamicPropertiesStore().getLatestBlockHeaderHash());
      }
    }
  }

  private void postContractTrigger(final TransactionTrace trace, boolean remove) {
    boolean isContractTriggerEnable = EventPluginLoader.getInstance()
        .isContractEventTriggerEnable() || EventPluginLoader
        .getInstance().isContractLogTriggerEnable();
    boolean isSolidityContractTriggerEnable = EventPluginLoader.getInstance()
        .isSolidityEventTriggerEnable() || EventPluginLoader
        .getInstance().isSolidityLogTriggerEnable();
    if (eventPluginLoaded
        && (isContractTriggerEnable || isSolidityContractTriggerEnable)) {
      // be careful, trace.getRuntimeResult().getTriggerList() should never return null
      for (ContractTrigger trigger : trace.getRuntimeResult().getTriggerList()) {
        ContractTriggerCapsule contractTriggerCapsule = new ContractTriggerCapsule(trigger);
        contractTriggerCapsule.getContractTrigger().setRemoved(remove);
        contractTriggerCapsule.setLatestSolidifiedBlockNumber(getDynamicPropertiesStore()
            .getLatestSolidifiedBlockNum());
        if (!triggerCapsuleQueue.offer(contractTriggerCapsule)) {
          logger
              .info("too many triggers, contract log trigger lost: {}", trigger.getTransactionId());
        }
      }
    }
  }

  private void prepareStoreFactory() {
    StoreFactory.init();
    StoreFactory.getInstance().setChainBaseManager(chainBaseManager);
  }

  private static class ValidateSignTask implements Callable<Boolean> {

    private TransactionCapsule stb;
    private CountDownLatch countDownLatch;
    private ChainBaseManager manager;

    ValidateSignTask(TransactionCapsule stb, CountDownLatch countDownLatch,
        ChainBaseManager manager) {
      this.stb = stb;
      this.countDownLatch = countDownLatch;
      this.manager = manager;
    }

    @Override
    public Boolean call() throws ValidateSignatureException {
      try {
        stb.validateSignature(manager.getAccountStore(), manager.getDynamicPropertiesStore());
      } catch (ValidateSignatureException e) {
        throw e;
      } finally {
        countDownLatch.countDown();
      }
      return true;
    }
  }

  public TransactionCapsule getTxFromPending(String txId) {
    AtomicReference<TransactionCapsule> transactionCapsule = new AtomicReference<>();
    Sha256Hash txHash = Sha256Hash.wrap(ByteArray.fromHexString(txId));
    pendingTransactions.forEach(tx -> {
      if (tx.getTransactionId().equals(txHash)) {
        transactionCapsule.set(tx);
        return;
      }
    });
    if (transactionCapsule.get() != null) {
      return transactionCapsule.get();
    }
    rePushTransactions.forEach(tx -> {
      if (tx.getTransactionId().equals(txHash)) {
        transactionCapsule.set(tx);
        return;
      }
    });
    return transactionCapsule.get();
  }

  public Collection<String> getTxListFromPending() {
    Set<String> result = new HashSet<>();
    pendingTransactions.forEach(tx -> {
      result.add(tx.getTransactionId().toString());
    });
    rePushTransactions.forEach(tx -> {
      result.add(tx.getTransactionId().toString());
    });
    return result;
  }

  public long getPendingSize() {
    long value = getPendingTransactions().size() + getRePushTransactions().size()
        + getPoppedTransactions().size();
    return value;
  }
}
