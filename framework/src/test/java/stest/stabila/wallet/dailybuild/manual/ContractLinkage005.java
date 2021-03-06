package stest.stabila.wallet.dailybuild.manual;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI.AccountResourceMessage;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractLinkage005 {

  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey003);
  String contractName;
  String code;
  String abi;
  Long zeroForCycleCost;
  Long firstForCycleCost;
  Long secondForCycleCost;
  Long thirdForCycleCost;
  Long forthForCycleCost;
  Long fifthForCycleCost;
  Long zeroForCycleTimes = 498L;
  Long firstForCycleTimes = 500L;
  Long secondForCycleTimes = 502L;
  Long thirdForCycleTimes = 504L;
  Long forthForCycleTimes = 506L;
  Long fifthForCycleTimes = 508L;
  byte[] contractAddress;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage005Address = ecKey1.getAddress();
  String linkage005Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(linkage005Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true, description = "Every same trigger use same ucr and net")
  public void testUcrCostDetail() {
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Assert.assertTrue(PublicMethed.sendcoin(linkage005Address, 5000000000000L, fromAddress,
        testKey003, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.cdBalance(linkage005Address, 250000000000L,
        0, linkage005Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(linkage005Address, 250000000000L,
        0, 1, linkage005Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull);
    Account info;
    info = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrLimit = resourceInfo.getUcrLimit();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeFreeNetLimit = resourceInfo.getFreeNetLimit();
    Long beforeNetLimit = resourceInfo.getNetLimit();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrLimit:" + beforeUcrLimit);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeFreeNetLimit:" + beforeFreeNetLimit);
    logger.info("beforeNetLimit:" + beforeNetLimit);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    String filePath = "./src/test/resources/soliditycode/contractLinkage005.sol";
    String contractName = "timeoutTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit, 0L, 100, null, linkage005Key,
        linkage005Address, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("Deploy ucrtotal is " + infoById.get().getReceipt().getUcrUsageTotal());

    Account infoafter = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrLimit = resourceInfoafter.getUcrLimit();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterFreeNetLimit = resourceInfoafter.getFreeNetLimit();
    Long afterNetLimit = resourceInfoafter.getNetLimit();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrLimit:" + afterUcrLimit);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterFreeNetLimit:" + afterFreeNetLimit);
    logger.info("afterNetLimit:" + afterNetLimit);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("---------------:");
    long fee = infoById.get().getFee();

    Assert.assertTrue(beforeBalance - fee == afterBalance);
    //Assert.assertTrue(afterUcrUsed > 0);
    //Assert.assertTrue(afterFreeNetUsed > 0);
    firstForCycleTimes = 1000L;
    secondForCycleTimes = 1002L;
    thirdForCycleTimes = 1004L;

    AccountResourceMessage resourceInfo1 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull);
    Account info1 = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
    Long beforeBalance1 = info1.getBalance();
    Long beforeUcrLimit1 = resourceInfo1.getUcrLimit();
    Long beforeUcrUsed1 = resourceInfo1.getUcrUsed();
    Long beforeFreeNetLimit1 = resourceInfo1.getFreeNetLimit();
    Long beforeNetLimit1 = resourceInfo1.getNetLimit();
    Long beforeNetUsed1 = resourceInfo1.getNetUsed();
    Long beforeFreeNetUsed1 = resourceInfo1.getFreeNetUsed();
    logger.info("beforeBalance1:" + beforeBalance1);
    logger.info("beforeUcrLimit1:" + beforeUcrLimit1);
    logger.info("beforeUcrUsed1:" + beforeUcrUsed1);
    logger.info("beforeFreeNetLimit1:" + beforeFreeNetLimit1);
    logger.info("beforeNetLimit1:" + beforeNetLimit1);
    logger.info("beforeNetUsed1:" + beforeNetUsed1);
    logger.info("beforeFreeNetUsed1:" + beforeFreeNetUsed1);
    byte[] contractAddress = infoById.get().getContractAddress().toByteArray();
    txid = PublicMethed.triggerContract(contractAddress,
        "testUseCpu(uint256)", firstForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Account infoafter1 = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull1);
    Long afterBalance1 = infoafter1.getBalance();
    Long afterUcrLimit1 = resourceInfoafter1.getUcrLimit();
    Long afterUcrUsed1 = resourceInfoafter1.getUcrUsed();
    Long afterFreeNetLimit1 = resourceInfoafter1.getFreeNetLimit();
    Long afterNetLimit1 = resourceInfoafter1.getNetLimit();
    Long afterNetUsed1 = resourceInfoafter1.getNetUsed();
    Long afterFreeNetUsed1 = resourceInfoafter1.getFreeNetUsed();
    logger.info("afterBalance1:" + afterBalance1);
    logger.info("afterUcrLimit1:" + afterUcrLimit1);
    logger.info("afterUcrUsed1:" + afterUcrUsed1);
    logger.info("afterFreeNetLimit1:" + afterFreeNetLimit1);
    logger.info("afterNetLimit1:" + afterNetLimit1);
    logger.info("afterNetUsed1:" + afterNetUsed1);
    logger.info("afterFreeNetUsed1:" + afterFreeNetUsed1);
    logger.info("---------------:");
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    fee = infoById.get().getFee();
    firstForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();
    Assert.assertTrue((beforeBalance1 - fee) == afterBalance1);
    Assert.assertTrue(afterUcrUsed1 > beforeUcrUsed1);
    Assert.assertTrue(afterNetUsed1 > beforeNetUsed1);
    //use UcrUsed and NetUsed.balance not change

    String txid6 = PublicMethed.triggerContract(contractAddress,
        "testUseCpu(uint256)", secondForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
    final String txid7 = PublicMethed.triggerContract(contractAddress,
        "testUseCpu(uint256)", thirdForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(txid6, blockingStubFull);
    secondForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid7, blockingStubFull);
    thirdForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    Assert.assertTrue(thirdForCycleCost - secondForCycleCost
        == secondForCycleCost - firstForCycleCost);

    zeroForCycleTimes = 498L;
    firstForCycleTimes = 500L;
    secondForCycleTimes = 502L;
    thirdForCycleTimes = 504L;
    forthForCycleTimes = 506L;
    fifthForCycleTimes = 508L;
    AccountResourceMessage resourceInfo4 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull);
    Account info4 = PublicMethed.queryAccount(linkage005Address, blockingStubFull);
    Long beforeBalance4 = info4.getBalance();
    Long beforeUcrLimit4 = resourceInfo4.getUcrLimit();
    Long beforeUcrUsed4 = resourceInfo4.getUcrUsed();
    Long beforeFreeNetLimit4 = resourceInfo4.getFreeNetLimit();
    Long beforeNetLimit4 = resourceInfo4.getNetLimit();
    Long beforeNetUsed4 = resourceInfo4.getNetUsed();
    Long beforeFreeNetUsed4 = resourceInfo4.getFreeNetUsed();
    logger.info("beforeBalance4:" + beforeBalance4);
    logger.info("beforeUcrLimit4:" + beforeUcrLimit4);
    logger.info("beforeUcrUsed4:" + beforeUcrUsed4);
    logger.info("beforeFreeNetLimit4:" + beforeFreeNetLimit4);
    logger.info("beforeNetLimit4:" + beforeNetLimit4);
    logger.info("beforeNetUsed4:" + beforeNetUsed4);
    logger.info("beforeFreeNetUsed4:" + beforeFreeNetUsed4);
    txid = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", zeroForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    fee = infoById.get().getFee();
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Account infoafter4 = PublicMethed.queryAccount(linkage005Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter4 = PublicMethed.getAccountResource(linkage005Address,
        blockingStubFull1);
    Long afterBalance4 = infoafter4.getBalance();
    Long afterUcrLimit4 = resourceInfoafter4.getUcrLimit();
    Long afterUcrUsed4 = resourceInfoafter4.getUcrUsed();
    Long afterFreeNetLimit4 = resourceInfoafter4.getFreeNetLimit();
    Long afterNetLimit4 = resourceInfoafter4.getNetLimit();
    Long afterNetUsed4 = resourceInfoafter4.getNetUsed();
    Long afterFreeNetUsed4 = resourceInfoafter4.getFreeNetUsed();
    logger.info("afterBalance4:" + afterBalance4);
    logger.info("afterUcrLimit4:" + afterUcrLimit4);
    logger.info("afterUcrUsed4:" + afterUcrUsed4);
    logger.info("afterFreeNetLimit4:" + afterFreeNetLimit4);
    logger.info("afterNetLimit4:" + afterNetLimit4);
    logger.info("afterNetUsed4:" + afterNetUsed4);
    logger.info("afterFreeNetUsed4:" + afterFreeNetUsed4);
    logger.info("---------------:");
    Assert.assertTrue(beforeBalance4 - fee == afterBalance4);
    Assert.assertTrue(afterUcrUsed4 > beforeUcrUsed4);
    Assert.assertTrue(afterNetUsed4 > beforeNetUsed4);

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    zeroForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    String txid1 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", firstForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid2 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", secondForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid3 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", thirdForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid4 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", forthForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    final String txid5 = PublicMethed.triggerContract(contractAddress,
        "testUseStorage(uint256)", fifthForCycleTimes.toString(), false,
        0, 100000000L, linkage005Address, linkage005Key, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(txid1, blockingStubFull);
    firstForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid2, blockingStubFull);
    secondForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid3, blockingStubFull);
    thirdForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid4, blockingStubFull);
    forthForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    infoById = PublicMethed.getTransactionInfoById(txid5, blockingStubFull);
    fifthForCycleCost = infoById.get().getReceipt().getUcrUsageTotal();

    Assert.assertTrue(thirdForCycleCost - secondForCycleCost
        == secondForCycleCost - firstForCycleCost);
    Assert.assertTrue(fifthForCycleCost - forthForCycleCost
        == forthForCycleCost - thirdForCycleCost);


  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.unCdBalance(linkage005Address, linkage005Key, 1,
        linkage005Address, blockingStubFull);
    PublicMethed.unCdBalance(linkage005Address, linkage005Key, 0,
        linkage005Address, blockingStubFull);
    PublicMethed.freedResource(linkage005Address, linkage005Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}


