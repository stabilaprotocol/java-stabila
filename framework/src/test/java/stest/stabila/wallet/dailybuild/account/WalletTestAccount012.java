package stest.stabila.wallet.dailybuild.account;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI.AccountResourceMessage;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.protos.Protocol.Account;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class WalletTestAccount012 {
  private static final long sendAmount = 10000000000L;
  private static final long frozenAmountForStabilaPower = 3456789L;
  private static final long frozenAmountForNet = 7000000L;
  private final String foundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] foundationAddress = PublicMethed.getFinalAddress(foundationKey);

  private final String witnessKey = Configuration.getByPath("testng.conf")
      .getString("witness.key1");
  private final byte[] witnessAddress = PublicMethed.getFinalAddress(witnessKey);

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] frozenAddress = ecKey1.getAddress();
  String frozenKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(frozenKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

  }

  @Test(enabled = true, description = "Freeze balance to get stabila power")
  public void test01FreezeBalanceGetStabilaPower() {


    final Long beforeFrozenTime = System.currentTimeMillis();
    Assert.assertTrue(PublicMethed.sendcoin(frozenAddress, sendAmount,
        foundationAddress, foundationKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);


    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    final Long beforeTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();
    final Long beforeStabilaPowerLimit = accountResource.getStabilaPowerLimit();


    Assert.assertTrue(PublicMethed.freezeBalanceGetStabilaPower(frozenAddress,frozenAmountForStabilaPower,
        0,2,null,frozenKey,blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceGetStabilaPower(frozenAddress,frozenAmountForNet,
        0,0,null,frozenKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long afterFrozenTime = System.currentTimeMillis();
    Account account = PublicMethed.queryAccount(frozenAddress,blockingStubFull);
    Assert.assertEquals(account.getStabilaPower().getFrozenBalance(),frozenAmountForStabilaPower);
    Assert.assertTrue(account.getStabilaPower().getExpireTime() > beforeFrozenTime
        && account.getStabilaPower().getExpireTime() < afterFrozenTime);

    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    Long afterTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();
    Long afterStabilaPowerLimit = accountResource.getStabilaPowerLimit();
    Long afterStabilaPowerUsed = accountResource.getStabilaPowerUsed();
    Assert.assertEquals(afterTotalStabilaPowerWeight - beforeTotalStabilaPowerWeight,
        frozenAmountForStabilaPower / 1000000L);

    Assert.assertEquals(afterStabilaPowerLimit - beforeStabilaPowerLimit,
        frozenAmountForStabilaPower / 1000000L);



    Assert.assertTrue(PublicMethed.freezeBalanceGetStabilaPower(frozenAddress,
        6000000 - frozenAmountForStabilaPower,
        0,2,null,frozenKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    afterStabilaPowerLimit = accountResource.getStabilaPowerLimit();

    Assert.assertEquals(afterStabilaPowerLimit - beforeStabilaPowerLimit,
        6);


  }


  @Test(enabled = true,description = "Vote witness by stabila power")
  public void test02VotePowerOnlyComeFromStabilaPower() {
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    final Long beforeStabilaPowerUsed = accountResource.getStabilaPowerUsed();


    HashMap<byte[],Long> witnessMap = new HashMap<>();
    witnessMap.put(witnessAddress,frozenAmountForNet / 1000000L);
    Assert.assertFalse(PublicMethed.voteWitness(frozenAddress,frozenKey,witnessMap,
        blockingStubFull));
    witnessMap.put(witnessAddress,frozenAmountForStabilaPower / 1000000L);
    Assert.assertTrue(PublicMethed.voteWitness(frozenAddress,frozenKey,witnessMap,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    Long afterStabilaPowerUsed = accountResource.getStabilaPowerUsed();
    Assert.assertEquals(afterStabilaPowerUsed - beforeStabilaPowerUsed,
        frozenAmountForStabilaPower / 1000000L);

    final Long secondBeforeStabilaPowerUsed = afterStabilaPowerUsed;
    witnessMap.put(witnessAddress,(frozenAmountForStabilaPower / 1000000L) - 1);
    Assert.assertTrue(PublicMethed.voteWitness(frozenAddress,frozenKey,witnessMap,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    afterStabilaPowerUsed = accountResource.getStabilaPowerUsed();
    Assert.assertEquals(secondBeforeStabilaPowerUsed - afterStabilaPowerUsed,
        1);


  }

  @Test(enabled = true,description = "Stabila power is not allow to others")
  public void test03StabilaPowerIsNotAllowToOthers() {
    Assert.assertFalse(PublicMethed.freezeBalanceGetStabilaPower(frozenAddress,
        frozenAmountForStabilaPower, 0,2,
        ByteString.copyFrom(foundationAddress),frozenKey,blockingStubFull));
  }


  @Test(enabled = true,description = "Unfreeze balance for stabila power")
  public void test04UnfreezeBalanceForStabilaPower() {
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(foundationAddress, blockingStubFull);
    final Long beforeTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();


    Assert.assertTrue(PublicMethed.unFreezeBalance(frozenAddress,frozenKey,2,
        null,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    Long afterTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();
    Assert.assertEquals(beforeTotalStabilaPowerWeight - afterTotalStabilaPowerWeight,
        6);

    Assert.assertEquals(accountResource.getStabilaPowerLimit(),0L);
    Assert.assertEquals(accountResource.getStabilaPowerUsed(),0L);

    Account account = PublicMethed.queryAccount(frozenAddress,blockingStubFull);
    Assert.assertEquals(account.getStabilaPower().getFrozenBalance(),0);


  }
  

  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    PublicMethed.unFreezeBalance(frozenAddress, frozenKey, 2, null,
        blockingStubFull);
    PublicMethed.unFreezeBalance(frozenAddress, frozenKey, 0, null,
        blockingStubFull);
    PublicMethed.freedResource(frozenAddress, frozenKey, foundationAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


