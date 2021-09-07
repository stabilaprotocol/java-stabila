package org.stabila.core;

import static org.stabila.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ForkController;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.zksnark.MerkleContainer;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.BlockCapsule.BlockId;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.capsule.utils.AssetUtil;
import org.stabila.core.capsule.utils.BlockUtil;
import org.stabila.core.db.BlockIndexStore;
import org.stabila.core.db.BlockStore;
import org.stabila.core.db.CommonDataBase;
import org.stabila.core.db.CommonStore;
import org.stabila.core.db.KhaosDatabase;
import org.stabila.core.db.PbftSignDataStore;
import org.stabila.core.db.RecentBlockStore;
import org.stabila.core.db.TransactionStore;
import org.stabila.core.db2.core.ITronChainBase;
import org.stabila.core.exception.BadItemException;
import org.stabila.core.exception.HeaderNotFound;
import org.stabila.core.exception.ItemNotFoundException;
import org.stabila.core.service.MortgageService;
import org.stabila.core.store.AbiStore;
import org.stabila.core.store.AccountAssetStore;
import org.stabila.core.store.AccountIdIndexStore;
import org.stabila.core.store.AccountIndexStore;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.AccountTraceStore;
import org.stabila.core.store.AssetIssueStore;
import org.stabila.core.store.AssetIssueV2Store;
import org.stabila.core.store.BalanceTraceStore;
import org.stabila.core.store.CodeStore;
import org.stabila.core.store.ContractStore;
import org.stabila.core.store.DelegatedResourceAccountIndexStore;
import org.stabila.core.store.DelegatedResourceStore;
import org.stabila.core.store.DelegationStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.ExchangeStore;
import org.stabila.core.store.ExchangeV2Store;
import org.stabila.core.store.IncrementalMerkleTreeStore;
import org.stabila.core.store.MarketAccountStore;
import org.stabila.core.store.MarketOrderStore;
import org.stabila.core.store.MarketPairPriceToOrderStore;
import org.stabila.core.store.MarketPairToPriceStore;
import org.stabila.core.store.NullifierStore;
import org.stabila.core.store.ProposalStore;
import org.stabila.core.store.StorageRowStore;
import org.stabila.core.store.TransactionHistoryStore;
import org.stabila.core.store.TransactionRetStore;
import org.stabila.core.store.TreeBlockIndexStore;
import org.stabila.core.store.VotesStore;
import org.stabila.core.store.WitnessScheduleStore;
import org.stabila.core.store.WitnessStore;
import org.stabila.core.store.ZKProofStore;

@Slf4j(topic = "DB")
@Component
public class ChainBaseManager {

  // db store
  @Autowired
  @Getter
  private AccountStore accountStore;
  @Autowired
  @Getter
  private AccountAssetStore accountAssetStore;
  @Autowired
  @Getter
  private BlockStore blockStore;
  @Autowired
  @Getter
  private WitnessStore witnessStore;
  @Autowired
  @Getter
  private AssetIssueStore assetIssueStore;
  @Autowired
  @Getter
  private AssetIssueV2Store assetIssueV2Store;
  @Autowired
  @Getter
  private DynamicPropertiesStore dynamicPropertiesStore;
  @Autowired
  @Getter
  private BlockIndexStore blockIndexStore;
  @Autowired
  @Getter
  private AccountIdIndexStore accountIdIndexStore;
  @Autowired
  @Getter
  private AccountIndexStore accountIndexStore;
  @Autowired
  @Getter
  private WitnessScheduleStore witnessScheduleStore;
  @Autowired
  @Getter
  private VotesStore votesStore;
  @Autowired
  @Getter
  private ProposalStore proposalStore;
  @Autowired
  @Getter
  private ExchangeStore exchangeStore;
  @Autowired
  @Getter
  private ExchangeV2Store exchangeV2Store;
  @Autowired
  @Getter
  private MarketAccountStore marketAccountStore;
  @Autowired
  @Getter
  private MarketOrderStore marketOrderStore;
  @Autowired
  @Getter
  private MarketPairPriceToOrderStore marketPairPriceToOrderStore;
  @Autowired
  @Getter
  private MarketPairToPriceStore marketPairToPriceStore;
  @Autowired
  @Getter
  private AbiStore abiStore;
  @Autowired
  @Getter
  private CodeStore codeStore;
  @Autowired
  @Getter
  private ContractStore contractStore;
  @Autowired
  @Getter
  private DelegatedResourceStore delegatedResourceStore;
  @Autowired
  @Getter
  private DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore;
  @Autowired
  @Getter
  private StorageRowStore storageRowStore;
  @Autowired
  @Getter
  private NullifierStore nullifierStore;
  @Autowired
  @Getter
  private ZKProofStore proofStore;

  @Autowired
  @Getter
  private IncrementalMerkleTreeStore merkleTreeStore;

  @Getter
  @Setter
  private MerkleContainer merkleContainer;

  @Getter
  @Setter
  private MortgageService mortgageService;

  @Autowired
  @Getter
  private DelegationStore delegationStore;

  @Autowired
  @Getter
  private KhaosDatabase khaosDb;

  @Autowired
  @Getter
  private CommonStore commonStore;

  @Autowired
  @Getter
  private TransactionStore transactionStore;
  @Autowired
  @Getter
  private TransactionRetStore transactionRetStore;
  @Autowired
  @Getter
  private RecentBlockStore recentBlockStore;
  @Autowired
  @Getter
  private TransactionHistoryStore transactionHistoryStore;

  @Getter
  @Setter
  private BlockCapsule genesisBlock;

  @Autowired
  @Getter
  private CommonDataBase commonDataBase;

  @Autowired
  @Getter
  private PbftSignDataStore pbftSignDataStore;

  @Autowired
  @Getter
  private BalanceTraceStore balanceTraceStore;

  @Autowired
  @Getter
  private AccountTraceStore accountTraceStore;

  @Getter
  private ForkController forkController = ForkController.instance();

  @Autowired
  @Getter
  @Setter
  private TreeBlockIndexStore merkleTreeIndexStore;

  public void closeOneStore(ITronChainBase database) {
    logger.info("******** begin to close " + database.getName() + " ********");
    try {
      database.close();
    } catch (Exception e) {
      logger.info("failed to close  " + database.getName() + ". " + e);
    } finally {
      logger.info("******** end to close " + database.getName() + " ********");
    }
  }

  public void closeAllStore() {
    closeOneStore(transactionRetStore);
    closeOneStore(recentBlockStore);
    closeOneStore(transactionHistoryStore);
    closeOneStore(transactionStore);
    closeOneStore(accountStore);
    closeOneStore(blockStore);
    closeOneStore(blockIndexStore);
    closeOneStore(accountIdIndexStore);
    closeOneStore(accountIndexStore);
    closeOneStore(witnessScheduleStore);
    closeOneStore(assetIssueStore);
    closeOneStore(dynamicPropertiesStore);
    closeOneStore(abiStore);
    closeOneStore(codeStore);
    closeOneStore(contractStore);
    closeOneStore(storageRowStore);
    closeOneStore(exchangeStore);
    closeOneStore(proposalStore);
    closeOneStore(votesStore);
    closeOneStore(delegatedResourceStore);
    closeOneStore(delegatedResourceAccountIndexStore);
    closeOneStore(assetIssueV2Store);
    closeOneStore(exchangeV2Store);
    closeOneStore(nullifierStore);
    closeOneStore(merkleTreeStore);
    closeOneStore(delegationStore);
    closeOneStore(proofStore);
    closeOneStore(commonStore);
    closeOneStore(commonDataBase);
    closeOneStore(pbftSignDataStore);
  }

  // for test only
  public List<ByteString> getWitnesses() {
    return witnessScheduleStore.getActiveWitnesses();
  }

  // for test only
  public void addWitness(final ByteString address) {
    List<ByteString> witnessAddresses =
        witnessScheduleStore.getActiveWitnesses();
    witnessAddresses.add(address);
    getWitnessScheduleStore().saveActiveWitnesses(witnessAddresses);
  }

  public BlockCapsule getHead() throws HeaderNotFound {
    List<BlockCapsule> blocks = getBlockStore().getBlockByLatestNum(1);
    if (CollectionUtils.isNotEmpty(blocks)) {
      return blocks.get(0);
    } else {
      logger.info("Header block Not Found");
      throw new HeaderNotFound("Header block Not Found");
    }
  }

  public synchronized BlockId getHeadBlockId() {
    return new BlockId(
        dynamicPropertiesStore.getLatestBlockHeaderHash(),
        dynamicPropertiesStore.getLatestBlockHeaderNumber());
  }

  public long getHeadBlockNum() {
    return dynamicPropertiesStore.getLatestBlockHeaderNumber();
  }

  public long getHeadBlockTimeStamp() {
    return dynamicPropertiesStore.getLatestBlockHeaderTimestamp();
  }

  public void initGenesis() {
    genesisBlock = BlockUtil.newGenesisBlockCapsule();
  }

  public long getHeadSlot() {
    return (getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - getGenesisBlock()
        .getTimeStamp()) / BLOCK_PRODUCED_INTERVAL;
  }


  /**
   * judge id.
   *
   * @param blockHash blockHash
   */
  public boolean containBlock(final Sha256Hash blockHash) {
    try {
      return this.khaosDb.containBlockInMiniStore(blockHash)
          || getBlockStore()
          .get(blockHash.getBytes()) != null;
    } catch (ItemNotFoundException | BadItemException e) {
      return false;
    }
  }

  public boolean containBlockInMainChain(BlockId blockId) {
    try {
      return getBlockStore().get(blockId.getBytes()) != null;
    } catch (ItemNotFoundException | BadItemException e) {
      return false;
    }
  }


  /**
   * Get a BlockCapsule by id.
   */
  public BlockCapsule getBlockById(final Sha256Hash hash)
      throws BadItemException, ItemNotFoundException {
    BlockCapsule block = this.khaosDb.getBlock(hash);
    if (block == null) {
      block = getBlockStore().get(hash.getBytes());
    }
    return block;
  }

  /**
   * judge has blocks.
   */
  public boolean hasBlocks() {
    return getBlockStore().isNotEmpty() || this.khaosDb.hasData();
  }

  public void setBlockReference(TransactionCapsule trans) {
    byte[] headHash = getDynamicPropertiesStore().getLatestBlockHeaderHash().getBytes();
    long headNum = getDynamicPropertiesStore().getLatestBlockHeaderNumber();
    trans.setReference(headNum, headHash);
  }

  public BlockId getSolidBlockId() {
    try {
      long num = getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
      return getBlockIdByNum(num);
    } catch (Exception e) {
      return getGenesisBlockId();
    }
  }

  public BlockId getGenesisBlockId() {
    return getGenesisBlock().getBlockId();
  }


  /**
   * Get the block id from the number.
   */
  public BlockId getBlockIdByNum(final long num) throws ItemNotFoundException {
    return getBlockIndexStore().get(num);
  }

  public BlockCapsule getBlockByNum(final long num) throws
      ItemNotFoundException, BadItemException {
    return getBlockById(getBlockIdByNum(num));
  }

  public void init() {
    AssetUtil.setAccountAssetStore(accountAssetStore);
    AssetUtil.setDynamicPropertiesStore(dynamicPropertiesStore);
  }
}
