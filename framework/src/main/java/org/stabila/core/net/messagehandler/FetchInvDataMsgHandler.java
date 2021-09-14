package org.stabila.core.net.messagehandler;

import static org.stabila.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.common.overlay.discover.node.statistics.MessageCount;
import org.stabila.common.overlay.message.Message;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.consensus.ConsensusDelegate;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.BlockCapsule.BlockId;
import org.stabila.core.capsule.PbftSignCapsule;
import org.stabila.core.config.Parameter.NetConstants;
import org.stabila.core.exception.P2pException;
import org.stabila.core.exception.P2pException.TypeEnum;
import org.stabila.core.net.StabilaNetDelegate;
import org.stabila.core.net.message.BlockMessage;
import org.stabila.core.net.message.FetchInvDataMessage;
import org.stabila.core.net.message.MessageTypes;
import org.stabila.core.net.message.PbftCommitMessage;
import org.stabila.core.net.message.TransactionMessage;
import org.stabila.core.net.message.TransactionsMessage;
import org.stabila.core.net.message.StabilaMessage;
import org.stabila.core.net.peer.Item;
import org.stabila.core.net.peer.PeerConnection;
import org.stabila.core.net.service.AdvService;
import org.stabila.core.net.service.SyncService;
import org.stabila.protos.Protocol.Inventory.InventoryType;
import org.stabila.protos.Protocol.PBFTMessage.Raw;
import org.stabila.protos.Protocol.ReasonCode;
import org.stabila.protos.Protocol.Transaction;

@Slf4j(topic = "net")
@Component
public class FetchInvDataMsgHandler implements StabilaMsgHandler {

  private volatile Cache<Long, Boolean> epochCache = CacheBuilder.newBuilder().initialCapacity(100)
      .maximumSize(1000).expireAfterWrite(1, TimeUnit.HOURS).build();

  private static final int MAX_SIZE = 1_000_000;
  @Autowired
  private StabilaNetDelegate stabilaNetDelegate;
  @Autowired
  private SyncService syncService;
  @Autowired
  private AdvService advService;
  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Override
  public void processMessage(PeerConnection peer, StabilaMessage msg) throws P2pException {

    FetchInvDataMessage fetchInvDataMsg = (FetchInvDataMessage) msg;

    check(peer, fetchInvDataMsg);

    InventoryType type = fetchInvDataMsg.getInventoryType();
    List<Transaction> transactions = Lists.newArrayList();

    int size = 0;

    for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
      Item item = new Item(hash, type);
      Message message = advService.getMessage(item);
      if (message == null) {
        try {
          message = stabilaNetDelegate.getData(hash, type);
        } catch (Exception e) {
          logger.error("Fetch item {} failed. reason: {}", item, hash, e.getMessage());
          peer.disconnect(ReasonCode.FETCH_FAIL);
          return;
        }
      }

      if (type == InventoryType.BLOCK) {
        BlockId blockId = ((BlockMessage) message).getBlockCapsule().getBlockId();
        if (peer.getBlockBothHave().getNum() < blockId.getNum()) {
          peer.setBlockBothHave(blockId);
        }
        sendPbftCommitMessage(peer, ((BlockMessage) message).getBlockCapsule());
        peer.sendMessage(message);
      } else {
        transactions.add(((TransactionMessage) message).getTransactionCapsule().getInstance());
        size += ((TransactionMessage) message).getTransactionCapsule().getInstance()
            .getSerializedSize();
        if (size > MAX_SIZE) {
          peer.sendMessage(new TransactionsMessage(transactions));
          transactions = Lists.newArrayList();
          size = 0;
        }
      }
    }
    if (!transactions.isEmpty()) {
      peer.sendMessage(new TransactionsMessage(transactions));
    }
  }

  private void sendPbftCommitMessage(PeerConnection peer, BlockCapsule blockCapsule) {
    try {
      if (!stabilaNetDelegate.allowPBFT() || peer.isSyncFinish()) {
        return;
      }
      long epoch = 0;
      PbftSignCapsule pbftSignCapsule = stabilaNetDelegate
          .getBlockPbftCommitData(blockCapsule.getNum());
      long maintenanceTimeInterval = consensusDelegate.getDynamicPropertiesStore()
          .getMaintenanceTimeInterval();
      if (pbftSignCapsule != null) {
        Raw raw = Raw.parseFrom(pbftSignCapsule.getPbftCommitResult().getData());
        epoch = raw.getEpoch();
        peer.sendMessage(new PbftCommitMessage(pbftSignCapsule));
      } else {
        epoch =
            (blockCapsule.getTimeStamp() / maintenanceTimeInterval + 1) * maintenanceTimeInterval;
      }
      if (epochCache.getIfPresent(epoch) == null) {
        PbftSignCapsule srl = stabilaNetDelegate.getSRLPbftCommitData(epoch);
        if (srl != null) {
          epochCache.put(epoch, true);
          peer.sendMessage(new PbftCommitMessage(srl));
        }
      }
    } catch (Exception e) {
      logger.error("", e);
    }
  }

  private void check(PeerConnection peer, FetchInvDataMessage fetchInvDataMsg) throws P2pException {
    MessageTypes type = fetchInvDataMsg.getInvMessageType();

    if (type == MessageTypes.TRX) {
      for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
        if (peer.getAdvInvSpread().getIfPresent(new Item(hash, InventoryType.TRX)) == null) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "not spread inv: {}" + hash);
        }
      }
      int fetchCount = peer.getNodeStatistics().messageStatistics.stabilaInStbFetchInvDataElement
          .getCount(10);
      int maxCount = advService.getStbCount().getCount(60);
      if (fetchCount > maxCount) {
        logger.error("maxCount: " + maxCount + ", fetchCount: " + fetchCount);
      }
    } else {
      boolean isAdv = true;
      for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
        if (peer.getAdvInvSpread().getIfPresent(new Item(hash, InventoryType.BLOCK)) == null) {
          isAdv = false;
          break;
        }
      }
      if (isAdv) {
        MessageCount stabilaOutAdvBlock = peer.getNodeStatistics().messageStatistics.stabilaOutAdvBlock;
        stabilaOutAdvBlock.add(fetchInvDataMsg.getHashList().size());
        int outBlockCountIn1min = stabilaOutAdvBlock.getCount(60);
        int producedBlockIn2min = 120_000 / BLOCK_PRODUCED_INTERVAL;
        if (outBlockCountIn1min > producedBlockIn2min) {
          logger.error("producedBlockIn2min: " + producedBlockIn2min + ", outBlockCountIn1min: "
              + outBlockCountIn1min);
        }
      } else {
        if (!peer.isNeedSyncFromUs()) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "no need sync");
        }
        for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
          long blockNum = new BlockId(hash).getNum();
          long minBlockNum =
              peer.getLastSyncBlockId().getNum() - 2 * NetConstants.SYNC_FETCH_BATCH_NUM;
          if (blockNum < minBlockNum) {
            throw new P2pException(TypeEnum.BAD_MESSAGE,
                "minBlockNum: " + minBlockNum + ", blockNum: " + blockNum);
          }
          if (peer.getSyncBlockIdCache().getIfPresent(hash) != null) {
            throw new P2pException(TypeEnum.BAD_MESSAGE,
                new BlockId(hash).getString() + " is exist");
          }
          peer.getSyncBlockIdCache().put(hash, System.currentTimeMillis());
        }
      }
    }
  }

}
