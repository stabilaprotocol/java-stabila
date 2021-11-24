package org.stabila.core.metrics.blockchain;

import com.codahale.metrics.Counter;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.ChainBaseManager;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.db.Manager;
import org.stabila.core.metrics.MetricsKey;
import org.stabila.core.metrics.MetricsUtil;
import org.stabila.core.metrics.net.RateInfo;
import org.stabila.protos.Protocol;

@Component
public class BlockChainMetricManager {


  @Autowired
  private Manager dbManager;

  @Autowired
  private ChainBaseManager chainBaseManager;

  private Map<String, BlockCapsule> executiveInfo = new ConcurrentHashMap<String, BlockCapsule>();

  @Getter
  private Map<String, Long> dupExecutiveBlockNum = new ConcurrentHashMap<String, Long>();
  @Setter
  private long failProcessBlockNum = 0;
  @Setter
  private String failProcessBlockReason = "";

  public BlockChainInfo getBlockChainInfo() {
    BlockChainInfo blockChainInfo = new BlockChainInfo();
    setBlockChainInfo(blockChainInfo);
    return blockChainInfo;
  }

  private void setBlockChainInfo(BlockChainInfo blockChain) {
    blockChain.setHeadBlockTimestamp(chainBaseManager.getHeadBlockTimeStamp());
    blockChain.setHeadBlockHash(dbManager.getDynamicPropertiesStore()
        .getLatestBlockHeaderHash().toString());

    RateInfo blockProcessTime = MetricsUtil.getRateInfo(MetricsKey.BLOCKCHAIN_BLOCK_PROCESS_TIME);
    blockChain.setBlockProcessTime(blockProcessTime);
    blockChain.setForkCount(getForkCount());
    blockChain.setFailForkCount(getFailForkCount());
    blockChain.setHeadBlockNum(chainBaseManager.getHeadBlockNum());
    blockChain.setTransactionCacheSize(dbManager.getPendingTransactions().size()
        + dbManager.getRePushTransactions().size());

    RateInfo missTx = MetricsUtil.getRateInfo(MetricsKey.BLOCKCHAIN_MISSED_TRANSACTION);
    blockChain.setMissedTransaction(missTx);

    RateInfo tpsInfo = MetricsUtil.getRateInfo(MetricsKey.BLOCKCHAIN_TPS);
    blockChain.setTps(tpsInfo);

    List<ExecutiveInfo> executives = getSrList();

    blockChain.setExecutives(executives);

    blockChain.setFailProcessBlockNum(failProcessBlockNum);
    blockChain.setFailProcessBlockReason(failProcessBlockReason);
    List<DupExecutiveInfo> dupExecutive = getDupExecutive();
    blockChain.setDupExecutive(dupExecutive);
  }

  public Protocol.MetricsInfo.BlockChainInfo getBlockChainProtoInfo() {
    Protocol.MetricsInfo.BlockChainInfo.Builder blockChainInfo =
        Protocol.MetricsInfo.BlockChainInfo.newBuilder();

    BlockChainInfo blockChain = getBlockChainInfo();
    blockChainInfo.setHeadBlockNum(blockChain.getHeadBlockNum());
    blockChainInfo.setHeadBlockTimestamp(blockChain.getHeadBlockTimestamp());
    blockChainInfo.setHeadBlockHash(blockChain.getHeadBlockHash());
    blockChainInfo.setFailProcessBlockNum(blockChain.getFailProcessBlockNum());
    blockChainInfo.setFailProcessBlockReason(blockChain.getFailProcessBlockReason());
    blockChainInfo.setForkCount(blockChain.getForkCount());
    blockChainInfo.setFailForkCount(blockChain.getFailForkCount());
    blockChainInfo.setTransactionCacheSize(blockChain.getTransactionCacheSize());
    RateInfo missTransaction = blockChain.getMissedTransaction();
    Protocol.MetricsInfo.RateInfo missTransactionInfo =
        missTransaction.toProtoEntity();
    blockChainInfo.setMissedTransaction(missTransactionInfo);

    RateInfo blockProcessTime = blockChain.getBlockProcessTime();
    Protocol.MetricsInfo.RateInfo blockProcessTimeInfo =
        blockProcessTime.toProtoEntity();
    blockChainInfo.setBlockProcessTime(blockProcessTimeInfo);
    RateInfo tps = blockChain.getTps();
    Protocol.MetricsInfo.RateInfo tpsInfo = tps.toProtoEntity();

    blockChainInfo.setTps(tpsInfo);
    for (ExecutiveInfo executive : blockChain.getExecutives()) {
      Protocol.MetricsInfo.BlockChainInfo.Executive.Builder executiveInfo =
          Protocol.MetricsInfo.BlockChainInfo.Executive.newBuilder();
      executiveInfo.setAddress(executive.getAddress());
      executiveInfo.setVersion(executive.getVersion());
      blockChainInfo.addExecutives(executiveInfo.build());
    }
    for (DupExecutiveInfo dupExecutive : blockChain.getDupExecutive()) {
      Protocol.MetricsInfo.BlockChainInfo.DupExecutive.Builder dupExecutiveInfo =
          Protocol.MetricsInfo.BlockChainInfo.DupExecutive.newBuilder();
      dupExecutiveInfo.setAddress(dupExecutive.getAddress());
      dupExecutiveInfo.setBlockNum(dupExecutive.getBlockNum());
      dupExecutiveInfo.setCount(dupExecutive.getCount());
      blockChainInfo.addDupExecutive(dupExecutiveInfo.build());
    }
    return blockChainInfo.build();

  }

  /**
   * apply block.
   *
   * @param block BlockCapsule
   */
  public void applyBlock(BlockCapsule block) {
    long nowTime = System.currentTimeMillis();
    String executiveAddress = Hex.toHexString(block.getExecutiveAddress().toByteArray());

    //executive info
    if (executiveInfo.containsKey(executiveAddress)) {
      BlockCapsule oldBlock = executiveInfo.get(executiveAddress);
      if ((!oldBlock.getBlockId().equals(block.getBlockId()))
          && oldBlock.getTimeStamp() == block.getTimeStamp()) {
        MetricsUtil.counterInc(MetricsKey.BLOCKCHAIN_DUP_EXECUTIVE + executiveAddress);
        dupExecutiveBlockNum.put(executiveAddress, block.getNum());
      }
    }
    executiveInfo.put(executiveAddress, block);

    //latency
    long netTime = nowTime - block.getTimeStamp();
    MetricsUtil.histogramUpdate(MetricsKey.NET_LATENCY, netTime);
    MetricsUtil.histogramUpdate(MetricsKey.NET_LATENCY_EXECUTIVE + executiveAddress, netTime);
    if (netTime >= 3000) {
      MetricsUtil.counterInc(MetricsKey.NET_LATENCY + ".3S");
      MetricsUtil.counterInc(MetricsKey.NET_LATENCY_EXECUTIVE + executiveAddress + ".3S");
    } else if (netTime >= 2000) {
      MetricsUtil.counterInc(MetricsKey.NET_LATENCY + ".2S");
      MetricsUtil.counterInc(MetricsKey.NET_LATENCY_EXECUTIVE + executiveAddress + ".2S");
    } else if (netTime >= 1000) {
      MetricsUtil.counterInc(MetricsKey.NET_LATENCY + ".1S");
      MetricsUtil.counterInc(MetricsKey.NET_LATENCY_EXECUTIVE + executiveAddress + ".1S");
    }

    //TPS
    if (block.getTransactions().size() > 0) {
      MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_TPS, block.getTransactions().size());
    }
  }

  private List<ExecutiveInfo> getSrList() {
    List<ExecutiveInfo> executiveInfos = new ArrayList<>();

    List<ByteString> executiveList = chainBaseManager.getExecutiveScheduleStore().getActiveExecutives();
    for (ByteString executiveAddress : executiveList) {
      String address = Hex.toHexString(executiveAddress.toByteArray());
      if (executiveInfo.containsKey(address)) {
        BlockCapsule block = executiveInfo.get(address);
        ExecutiveInfo executive = new ExecutiveInfo(address,
            block.getInstance().getBlockHeader().getRawData().getVersion());
        executiveInfos.add(executive);
      }
    }
    return executiveInfos;
  }


  public int getForkCount() {
    return (int) MetricsUtil.getMeter(MetricsKey.BLOCKCHAIN_FORK_COUNT).getCount();
  }

  public int getFailForkCount() {
    return (int) MetricsUtil.getMeter(MetricsKey.BLOCKCHAIN_FAIL_FORK_COUNT).getCount();
  }

  private List<DupExecutiveInfo> getDupExecutive() {
    List<DupExecutiveInfo> dupExecutives = new ArrayList<>();
    SortedMap<String, Counter> dupExecutiveMap =
        MetricsUtil.getCounters(MetricsKey.BLOCKCHAIN_DUP_EXECUTIVE);
    for (Map.Entry<String, Counter> entry : dupExecutiveMap.entrySet()) {
      DupExecutiveInfo dupExecutive = new DupExecutiveInfo();
      String executive = entry.getKey().substring(MetricsKey.BLOCKCHAIN_DUP_EXECUTIVE.length());
      long blockNum = dupExecutiveBlockNum.get(executive);
      dupExecutive.setAddress(executive);
      dupExecutive.setBlockNum(blockNum);
      dupExecutive.setCount((int) entry.getValue().getCount());
      dupExecutives.add(dupExecutive);
    }
    return dupExecutives;
  }
}
