package org.stabila.core.services;

import com.google.protobuf.ByteString;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.core.capsule.BlockCapsule;

public class ExecutiveProductBlockServiceTest {

  @Test
  public void GetSetCheatExecutiveInfoTest() {
    ExecutiveProductBlockService.CheatExecutiveInfo cheatExecutiveInfo =
        new ExecutiveProductBlockService.CheatExecutiveInfo();
    long time = System.currentTimeMillis();
    cheatExecutiveInfo.setTime(time);
    Assert.assertEquals(time, cheatExecutiveInfo.getTime());
    long latestBlockNum = 100L;
    cheatExecutiveInfo.setLatestBlockNum(latestBlockNum);
    Assert.assertEquals(latestBlockNum, cheatExecutiveInfo.getLatestBlockNum());
    Set<BlockCapsule> blockCapsuleSet = new HashSet<BlockCapsule>();
    cheatExecutiveInfo.setBlockCapsuleSet(blockCapsuleSet);
    Assert.assertEquals(blockCapsuleSet, cheatExecutiveInfo.getBlockCapsuleSet());
    BlockCapsule blockCapsule1 = new BlockCapsule(
        1,
        Sha256Hash.wrap(ByteString.copyFrom(
            ByteArray.fromHexString(
                "0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b81"))),
        1,
        ByteString.copyFromUtf8("testAddress"));
    // check after add one block
    cheatExecutiveInfo.add(blockCapsule1);
    blockCapsuleSet.add(blockCapsule1);
    Assert.assertEquals(blockCapsuleSet, cheatExecutiveInfo.getBlockCapsuleSet());
    cheatExecutiveInfo.clear();
    blockCapsuleSet.clear();
    Assert.assertEquals(blockCapsuleSet, cheatExecutiveInfo.getBlockCapsuleSet());
    // times increment check
    AtomicInteger times = new AtomicInteger(0);
    cheatExecutiveInfo.setTimes(times);
    Assert.assertEquals(times, cheatExecutiveInfo.getTimes());
    cheatExecutiveInfo.increment();
    times.incrementAndGet();
    Assert.assertEquals(times, cheatExecutiveInfo.getTimes());

    Assert.assertEquals("{"
        + "times=" + times.get()
        + ", time=" + time
        + ", latestBlockNum=" + latestBlockNum
        + ", blockCapsuleSet=" + blockCapsuleSet
        + '}', cheatExecutiveInfo.toString());
  }

  @Test
  public void validExecutiveProductTwoBlockTest() {
    ExecutiveProductBlockService executiveProductBlockService = new ExecutiveProductBlockService();
    BlockCapsule blockCapsule1 = new BlockCapsule(
        1,
        Sha256Hash.wrap(ByteString.copyFrom(
            ByteArray.fromHexString(
                "0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b81"))),
        1,
        ByteString.copyFromUtf8("testAddress"));
    executiveProductBlockService.validExecutiveProductTwoBlock(blockCapsule1);
    Assert.assertEquals(0, executiveProductBlockService.queryCheatExecutiveInfo().size());
    // different hash, same time and number
    BlockCapsule blockCapsule2 = new BlockCapsule(
        1,
        Sha256Hash.wrap(ByteString.copyFrom(
            ByteArray.fromHexString(
                "0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b82"))),
        1,
        ByteString.copyFromUtf8("testAddress"));

    executiveProductBlockService.validExecutiveProductTwoBlock(blockCapsule2);
    String key = ByteArray.toHexString(blockCapsule2.getExecutiveAddress().toByteArray());
    ExecutiveProductBlockService.CheatExecutiveInfo block =
        executiveProductBlockService.queryCheatExecutiveInfo().get(key);
    Assert.assertEquals(1, executiveProductBlockService.queryCheatExecutiveInfo().size());
    Assert.assertEquals(2, block.getBlockCapsuleSet().size());
    Assert.assertEquals(blockCapsule2.getNum(), block.getLatestBlockNum());

    Assert.assertEquals(block.getBlockCapsuleSet().contains(blockCapsule2), true);

    Iterator<BlockCapsule> iterator = block.getBlockCapsuleSet()
        .iterator();
    boolean isInner = false;
    while (iterator.hasNext()) {
      BlockCapsule blockCapsule = iterator.next();
      blockCapsule.getBlockId();
      if (blockCapsule.getBlockId().equals(blockCapsule1.getBlockId())) {
        isInner = true;
      }
    }
    Assert.assertTrue(isInner);
  }
}
