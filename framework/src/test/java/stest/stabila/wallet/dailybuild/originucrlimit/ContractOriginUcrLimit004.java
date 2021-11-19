package stest.stabila.wallet.dailybuild.originucrlimit;

import com.google.protobuf.ByteString;
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
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractOriginUcrLimit004 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] dev001Address = ecKey1.getAddress();
  String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] user001Address = ecKey2.getAddress();
  String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
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
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  private long getAvailableCdedUcr(byte[] accountAddress) {
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(accountAddress,
        blockingStubFull);
    long ucrLimit = resourceInfo.getUcrLimit();
    long ucrUsed = resourceInfo.getUcrUsed();
    return ucrLimit - ucrUsed;
  }

  private long getUserAvailableUcr(byte[] userAddress) {
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(userAddress,
        blockingStubFull);
    Account info = PublicMethed.queryAccount(userAddress, blockingStubFull);
    long balance = info.getBalance();
    long ucrLimit = resourceInfo.getUcrLimit();
    long userAvaliableCdedUcr = getAvailableCdedUcr(userAddress);
    return balance / 100 + userAvaliableCdedUcr;
  }

  private long getFeeLimit(String txid) {
    Optional<Transaction> trsById = PublicMethed.getTransactionById(txid, blockingStubFull);
    return trsById.get().getRawData().getFeeLimit();
  }

  private long getUserMax(byte[] userAddress, long feelimit) {
    logger.info("User feeLimit: " + feelimit / 100);
    logger.info("User UserAvaliableUcr: " + getUserAvailableUcr(userAddress));
    return Math.min(feelimit / 100, getUserAvailableUcr(userAddress));
  }

  private long getOriginalUcrLimit(byte[] contractAddress) {
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    return smartContract.getOriginUcrLimit();
  }

  private long getConsumeUserResourcePercent(byte[] contractAddress) {
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    return smartContract.getConsumeUserResourcePercent();
  }

  private long getDevMax(byte[] devAddress, byte[] userAddress, long feeLimit,
      byte[] contractAddress) {
    long devMax = Math.min(getAvailableCdedUcr(devAddress),
        getOriginalUcrLimit(contractAddress));
    long p = getConsumeUserResourcePercent(contractAddress);
    if (p != 0) {
      logger.info("p: " + p);
      devMax = Math.min(devMax, getUserMax(userAddress, feeLimit) * (100 - p) / p);
      logger.info("Dev byUserPercent: " + getUserMax(userAddress, feeLimit) * (100 - p) / p);
    }
    logger.info("Dev AvaliableCdedUcr: " + getAvailableCdedUcr(devAddress));
    logger.info("Dev OriginalUcrLimit: " + getOriginalUcrLimit(contractAddress));
    return devMax;
  }

  @Test(enabled = true, description = "Contract use Origin_ucr_limit")
  public void testOriginUcrLimit() {
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 1000000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 1000000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    // A2B1

    //dev balance and Ucr
    long devTargetBalance = 10_000_000;
    long devTargetUcr = 70000;

    // deploy contract parameters
    final long deployFeeLimit = maxFeeLimit;
    final long consumeUserResourcePercent = 0;
    final long originUcrLimit = 1000;

    //dev balance and Ucr
    final long devTriggerTargetBalance = 0;
    final long devTriggerTargetUcr = 592;

    // user balance and Ucr
    final long userTargetBalance = 0;
    final long userTargetUcr = 2000L;

    // trigger contract parameter, maxFeeLimit 10000000
    final long triggerFeeLimit = maxFeeLimit;
    final boolean expectRet = true;

    // count dev ucr, balance
    long devCdBalanceUnit = PublicMethed.getCdBalanceCount(dev001Address, dev001Key,
        devTargetUcr, blockingStubFull);

    long devNeedBalance = devTargetBalance + devCdBalanceUnit;

    logger.info("need balance:" + devNeedBalance);

    // get balance
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, devNeedBalance, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    // get ucr
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(dev001Address, devCdBalanceUnit,
        0, 1, dev001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long devUcrLimitBefore = accountResource.getUcrLimit();
    long devUcrUsageBefore = accountResource.getUcrUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("before deploy, dev ucr limit is " + Long.toString(devUcrLimitBefore));
    logger.info("before deploy, dev ucr usage is " + Long.toString(devUcrUsageBefore));
    logger.info("before deploy, dev balance is " + Long.toString(devBalanceBefore));

    String filePath = "src/test/resources/soliditycode/contractOriginUcrLimit004.sol";
    String contractName = "findArgsContractTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    final String deployTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            deployFeeLimit, 0L, consumeUserResourcePercent, originUcrLimit, "0",
            0, null, dev001Key, dev001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devUcrLimitAfter = accountResource.getUcrLimit();
    long devUcrUsageAfter = accountResource.getUcrUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("after deploy, dev ucr limit is " + Long.toString(devUcrLimitAfter));
    logger.info("after deploy, dev ucr usage is " + Long.toString(devUcrUsageAfter));
    logger.info("after deploy, dev balance is " + Long.toString(devBalanceAfter));

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(deployTxid, blockingStubFull);

    ByteString contractAddressString = infoById.get().getContractAddress();
    contractAddress = contractAddressString.toByteArray();
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);

    Assert.assertTrue(smartContract.getAbi() != null);

    Assert.assertTrue(devUcrLimitAfter > 0);
    Assert.assertTrue(devUcrUsageAfter > 0);
    Assert.assertEquals(devBalanceBefore, devBalanceAfter);

    // count dev ucr, balance
    devCdBalanceUnit = PublicMethed.getCdBalanceCount(dev001Address, dev001Key,
        devTriggerTargetUcr, blockingStubFull);

    devNeedBalance = devTriggerTargetBalance + devCdBalanceUnit;
    logger.info("dev need  balance:" + devNeedBalance);

    // count user ucr, balance
    long userCdBalanceUnit = PublicMethed.getCdBalanceCount(user001Address, user001Key,
        userTargetUcr, blockingStubFull);

    long userNeedBalance = userTargetBalance + userCdBalanceUnit;

    logger.info("User need  balance:" + userNeedBalance);

    // get balance
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, devNeedBalance, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, userNeedBalance, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    // get ucr
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(dev001Address, devCdBalanceUnit,
        0, 1, dev001Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(user001Address, userCdBalanceUnit,
        0, 1, user001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    devUcrLimitBefore = accountResource.getUcrLimit();
    devUcrUsageBefore = accountResource.getUcrUsed();
    devBalanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("before trigger, dev devUcrLimitBefore is "
        + Long.toString(devUcrLimitBefore));
    logger.info("before trigger, dev devUcrUsageBefore is "
        + Long.toString(devUcrUsageBefore));
    logger.info("before trigger, dev devBalanceBefore is " + Long.toString(devBalanceBefore));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitBefore = accountResource.getUcrLimit();
    long userUcrUsageBefore = accountResource.getUcrUsed();
    long userBalanceBefore = PublicMethed.queryAccount(
        user001Address, blockingStubFull).getBalance();

    logger.info("before trigger, user userUcrLimitBefore is "
        + Long.toString(userUcrLimitBefore));
    logger.info("before trigger, user userUcrUsageBefore is "
        + Long.toString(userUcrUsageBefore));
    logger.info("before trigger, user userBalanceBefore is " + Long.toString(userBalanceBefore));

    logger.info("==================================");
    long userMax = getUserMax(user001Address, triggerFeeLimit);
    long devMax = getDevMax(dev001Address, user001Address, triggerFeeLimit, contractAddress);

    logger.info("userMax: " + userMax);
    logger.info("devMax: " + devMax);
    logger.info("==================================");

    String param = "\"" + 0 + "\"";
    final String triggerTxid = PublicMethed
        .triggerContract(contractAddress, "findArgsByIndexTest(uint256)",
            param, false, 0, triggerFeeLimit,
            user001Address, user001Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    devUcrLimitAfter = accountResource.getUcrLimit();
    devUcrUsageAfter = accountResource.getUcrUsed();
    devBalanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("after trigger, dev devUcrLimitAfter is " + Long.toString(devUcrLimitAfter));
    logger.info("after trigger, dev devUcrUsageAfter is " + Long.toString(devUcrUsageAfter));
    logger.info("after trigger, dev devBalanceAfter is " + Long.toString(devBalanceAfter));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitAfter = accountResource.getUcrLimit();
    long userUcrUsageAfter = accountResource.getUcrUsed();
    long userBalanceAfter = PublicMethed.queryAccount(user001Address,
        blockingStubFull).getBalance();

    logger.info("after trigger, user userUcrLimitAfter is "
        + Long.toString(userUcrLimitAfter));
    logger.info("after trigger, user userUcrUsageAfter is "
        + Long.toString(userUcrUsageAfter));
    logger.info("after trigger, user userBalanceAfter is " + Long.toString(userBalanceAfter));

    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);
    boolean isSuccess = true;
    if (triggerTxid == null || infoById.get().getResultValue() != 0) {
      logger.info("transaction failed with message: " + infoById.get().getResMessage());
      isSuccess = false;
    }

    long fee = infoById.get().getFee();
    long ucrFee = infoById.get().getReceipt().getUcrFee();
    long ucrUsage = infoById.get().getReceipt().getUcrUsage();
    long originUcrUsage = infoById.get().getReceipt().getOriginUcrUsage();
    long ucrTotalUsage = infoById.get().getReceipt().getUcrUsageTotal();
    long netUsage = infoById.get().getReceipt().getNetUsage();
    long netFee = infoById.get().getReceipt().getNetFee();

    logger.info("fee: " + fee);
    logger.info("ucrFee: " + ucrFee);
    logger.info("ucrUsage: " + ucrUsage);
    logger.info("originUcrUsage: " + originUcrUsage);
    logger.info("ucrTotalUsage: " + ucrTotalUsage);
    logger.info("netUsage: " + netUsage);
    logger.info("netFee: " + netFee);

    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    long consumeUserPercent = smartContract.getConsumeUserResourcePercent();
    logger.info("ConsumeURPercent: " + consumeUserPercent);

    long devExpectCost = ucrTotalUsage * (100 - consumeUserPercent) / 100;
    long userExpectCost = ucrTotalUsage - devExpectCost;
    final long totalCost = devExpectCost + userExpectCost;

    logger.info("devExpectCost: " + devExpectCost);
    logger.info("userExpectCost: " + userExpectCost);

    Assert.assertTrue(devUcrLimitAfter > 0);
    Assert.assertEquals(devBalanceBefore, devBalanceAfter);

    // dev original is the dev max expense A2B1
    Assert.assertEquals(getOriginalUcrLimit(contractAddress), devMax);

    // DEV is enough to pay
    Assert.assertEquals(originUcrUsage, devExpectCost);
    //    Assert.assertEquals(devUcrUsageAfter,devExpectCost + devUcrUsageBefore);
    // User Ucr is enough to pay");
    Assert.assertEquals(ucrUsage, userExpectCost);
    Assert.assertEquals(userBalanceBefore, userBalanceAfter);
    Assert.assertEquals(userUcrUsageAfter, userUcrUsageBefore);
    Assert.assertEquals(userBalanceBefore, userBalanceAfter);
    Assert.assertEquals(totalCost, ucrTotalUsage);

    if (expectRet) {
      Assert.assertTrue(isSuccess);
    } else {
      Assert.assertFalse(isSuccess);
    }
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.unCdBalance(user001Address, user001Key, 1, user001Address, blockingStubFull);
    PublicMethed.unCdBalance(dev001Address, dev001Key, 1, dev001Address, blockingStubFull);
    PublicMethed.freedResource(user001Address, user001Key, fromAddress, blockingStubFull);
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


