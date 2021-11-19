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
  private static final long cdedAmountForStabilaPower = 3456789L;
  private static final long cdedAmountForNet = 7000000L;
  private final String foundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] foundationAddress = PublicMethed.getFinalAddress(foundationKey);

  private final String witnessKey = Configuration.getByPath("testng.conf")
      .getString("witness.key1");
  private final byte[] witnessAddress = PublicMethed.getFinalAddress(witnessKey);

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] cdedAddress = ecKey1.getAddress();
  String cdedKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(cdedKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

  }

  @Test(enabled = true, description = "Cd balance to get stabila power")
  public void test01CdBalanceGetStabilaPower() {


    final Long beforeCdedTime = System.currentTimeMillis();
    Assert.assertTrue(PublicMethed.sendcoin(cdedAddress, sendAmount,
        foundationAddress, foundationKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);


    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(cdedAddress, blockingStubFull);
    final Long beforeTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();
    final Long beforeStabilaPowerLimit = accountResource.getStabilaPowerLimit();


    Assert.assertTrue(PublicMethed.cdBalanceGetStabilaPower(cdedAddress,cdedAmountForStabilaPower,
        0,2,null,cdedKey,blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceGetStabilaPower(cdedAddress,cdedAmountForNet,
        0,0,null,cdedKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long afterCdedTime = System.currentTimeMillis();
    Account account = PublicMethed.queryAccount(cdedAddress,blockingStubFull);
    Assert.assertEquals(account.getStabilaPower().getCdedBalance(),cdedAmountForStabilaPower);
    Assert.assertTrue(account.getStabilaPower().getExpireTime() > beforeCdedTime
        && account.getStabilaPower().getExpireTime() < afterCdedTime);

    accountResource = PublicMethed
        .getAccountResource(cdedAddress, blockingStubFull);
    Long afterTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();
    Long afterStabilaPowerLimit = accountResource.getStabilaPowerLimit();
    Long afterStabilaPowerUsed = accountResource.getStabilaPowerUsed();
    Assert.assertEquals(afterTotalStabilaPowerWeight - beforeTotalStabilaPowerWeight,
        cdedAmountForStabilaPower / 1000000L);

    Assert.assertEquals(afterStabilaPowerLimit - beforeStabilaPowerLimit,
        cdedAmountForStabilaPower / 1000000L);



    Assert.assertTrue(PublicMethed.cdBalanceGetStabilaPower(cdedAddress,
        6000000 - cdedAmountForStabilaPower,
        0,2,null,cdedKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethed
        .getAccountResource(cdedAddress, blockingStubFull);
    afterStabilaPowerLimit = accountResource.getStabilaPowerLimit();

    Assert.assertEquals(afterStabilaPowerLimit - beforeStabilaPowerLimit,
        6);


  }


  @Test(enabled = true,description = "Vote witness by stabila power")
  public void test02VotePowerOnlyComeFromStabilaPower() {
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(cdedAddress, blockingStubFull);
    final Long beforeStabilaPowerUsed = accountResource.getStabilaPowerUsed();


    HashMap<byte[],Long> witnessMap = new HashMap<>();
    witnessMap.put(witnessAddress,cdedAmountForNet / 1000000L);
    Assert.assertFalse(PublicMethed.voteWitness(cdedAddress,cdedKey,witnessMap,
        blockingStubFull));
    witnessMap.put(witnessAddress,cdedAmountForStabilaPower / 1000000L);
    Assert.assertTrue(PublicMethed.voteWitness(cdedAddress,cdedKey,witnessMap,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed
        .getAccountResource(cdedAddress, blockingStubFull);
    Long afterStabilaPowerUsed = accountResource.getStabilaPowerUsed();
    Assert.assertEquals(afterStabilaPowerUsed - beforeStabilaPowerUsed,
        cdedAmountForStabilaPower / 1000000L);

    final Long secondBeforeStabilaPowerUsed = afterStabilaPowerUsed;
    witnessMap.put(witnessAddress,(cdedAmountForStabilaPower / 1000000L) - 1);
    Assert.assertTrue(PublicMethed.voteWitness(cdedAddress,cdedKey,witnessMap,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethed
        .getAccountResource(cdedAddress, blockingStubFull);
    afterStabilaPowerUsed = accountResource.getStabilaPowerUsed();
    Assert.assertEquals(secondBeforeStabilaPowerUsed - afterStabilaPowerUsed,
        1);


  }

  @Test(enabled = true,description = "Stabila power is not allow to others")
  public void test03StabilaPowerIsNotAllowToOthers() {
    Assert.assertFalse(PublicMethed.cdBalanceGetStabilaPower(cdedAddress,
        cdedAmountForStabilaPower, 0,2,
        ByteString.copyFrom(foundationAddress),cdedKey,blockingStubFull));
  }


  @Test(enabled = true,description = "Uncd balance for stabila power")
  public void test04UncdBalanceForStabilaPower() {
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(foundationAddress, blockingStubFull);
    final Long beforeTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();


    Assert.assertTrue(PublicMethed.unCdBalance(cdedAddress,cdedKey,2,
        null,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed
        .getAccountResource(cdedAddress, blockingStubFull);
    Long afterTotalStabilaPowerWeight = accountResource.getTotalStabilaPowerWeight();
    Assert.assertEquals(beforeTotalStabilaPowerWeight - afterTotalStabilaPowerWeight,
        6);

    Assert.assertEquals(accountResource.getStabilaPowerLimit(),0L);
    Assert.assertEquals(accountResource.getStabilaPowerUsed(),0L);

    Account account = PublicMethed.queryAccount(cdedAddress,blockingStubFull);
    Assert.assertEquals(account.getStabilaPower().getCdedBalance(),0);


  }
  

  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    PublicMethed.unCdBalance(cdedAddress, cdedKey, 2, null,
        blockingStubFull);
    PublicMethed.unCdBalance(cdedAddress, cdedKey, 0, null,
        blockingStubFull);
    PublicMethed.freedResource(cdedAddress, cdedKey, foundationAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


