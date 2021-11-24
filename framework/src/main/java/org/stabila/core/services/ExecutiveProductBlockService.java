package org.stabila.core.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.stabila.common.utils.ByteArray;
import org.stabila.core.capsule.BlockCapsule;

@Slf4j(topic = "executive")
@Service
public class ExecutiveProductBlockService {

  private Cache<Long, BlockCapsule> historyBlockCapsuleCache = CacheBuilder.newBuilder()
      .initialCapacity(200).maximumSize(200).build();

  private Map<String, CheatExecutiveInfo> cheatExecutiveInfoMap = new HashMap<>();

  public void validExecutiveProductTwoBlock(BlockCapsule block) {
    try {
      BlockCapsule blockCapsule = historyBlockCapsuleCache.getIfPresent(block.getNum());
      if (blockCapsule != null && Arrays.equals(blockCapsule.getExecutiveAddress().toByteArray(),
          block.getExecutiveAddress().toByteArray()) && !Arrays.equals(block.getBlockId().getBytes(),
          blockCapsule.getBlockId().getBytes())) {
        String key = ByteArray.toHexString(block.getExecutiveAddress().toByteArray());
        if (!cheatExecutiveInfoMap.containsKey(key)) {
          CheatExecutiveInfo cheatExecutiveInfo = new CheatExecutiveInfo();
          cheatExecutiveInfoMap.put(key, cheatExecutiveInfo);
        }
        cheatExecutiveInfoMap.get(key).clear().setTime(System.currentTimeMillis())
            .setLatestBlockNum(block.getNum()).add(block).add(blockCapsule).increment();
      } else {
        historyBlockCapsuleCache.put(block.getNum(), new BlockCapsule(block.getInstance()));
      }
    } catch (Exception e) {
      logger.error("valid executive same time product two block fail! blockNum: {}, blockHash: {}",
          block.getNum(), block.getBlockId().toString(), e);
    }
  }

  public Map<String, CheatExecutiveInfo> queryCheatExecutiveInfo() {
    return cheatExecutiveInfoMap;
  }

  public static class CheatExecutiveInfo {

    private AtomicInteger times = new AtomicInteger(0);
    private long latestBlockNum;
    private Set<BlockCapsule> blockCapsuleSet = new HashSet<>();
    private long time;

    public CheatExecutiveInfo increment() {
      times.incrementAndGet();
      return this;
    }

    public AtomicInteger getTimes() {
      return times;
    }

    public CheatExecutiveInfo setTimes(AtomicInteger times) {
      this.times = times;
      return this;
    }

    public long getLatestBlockNum() {
      return latestBlockNum;
    }

    public CheatExecutiveInfo setLatestBlockNum(long latestBlockNum) {
      this.latestBlockNum = latestBlockNum;
      return this;
    }

    public Set<BlockCapsule> getBlockCapsuleSet() {
      return new HashSet<>(blockCapsuleSet);
    }

    public CheatExecutiveInfo setBlockCapsuleSet(Set<BlockCapsule> blockCapsuleSet) {
      this.blockCapsuleSet = new HashSet<>(blockCapsuleSet);
      return this;
    }

    public CheatExecutiveInfo clear() {
      blockCapsuleSet.clear();
      return this;
    }

    public CheatExecutiveInfo add(BlockCapsule blockCapsule) {
      blockCapsuleSet.add(blockCapsule);
      return this;
    }

    public long getTime() {
      return time;
    }

    public CheatExecutiveInfo setTime(long time) {
      this.time = time;
      return this;
    }

    @Override
    public String toString() {
      return "{"
          + "times=" + times.get()
          + ", time=" + time
          + ", latestBlockNum=" + latestBlockNum
          + ", blockCapsuleSet=" + blockCapsuleSet
          + '}';
    }
  }
}
