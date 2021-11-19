package stest.stabila.wallet.dailybuild.svmnewcommand.create2;

import static org.hamcrest.core.StringContains.containsString;

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
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class Create2Test003 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  private byte[] factoryContractAddress = null;
  private byte[] testContractAddress = null;

  private ECKey ecKey1 = new ECKey(Utils.getRandom());
  private byte[] dev001Address = ecKey1.getAddress();
  private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  private ECKey ecKey2 = new ECKey(Utils.getRandom());
  private byte[] user001Address = ecKey2.getAddress();
  private String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

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

    PublicMethed.printAddress(dev001Key);
    PublicMethed.printAddress(user001Key);
  }

  @Test(enabled = true, description = "Deploy factory contract")
  public void test01DeployFactoryContract() {
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(dev001Address, dev001Key, 170000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress, 10_000_000L,
        0, 0, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String filePath = "./src/test/resources/soliditycode/create2contract.sol";
    String contractName = "Factory";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    final String transferTokenTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 0, 10000,
            "0", 0, null, dev001Key,
            dev001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);

    if (infoById.get().getResultValue() != 0) {
      Assert.fail("deploy transaction failed with message: " + infoById.get().getResMessage());
    }

    TransactionInfo transactionInfo = infoById.get();
    logger.info("UcrUsageTotal: " + transactionInfo.getReceipt().getUcrUsageTotal());
    logger.info("NetUsage: " + transactionInfo.getReceipt().getNetUsage());

    factoryContractAddress = infoById.get().getContractAddress().toByteArray();
    SmartContract smartContract = PublicMethed.getContract(factoryContractAddress,
        blockingStubFull);
    Assert.assertNotNull(smartContract.getAbi());
  }

  @Test(enabled = true, description = "Trigger create2 command with invalid bytecode")
  public void test02TriggerCreate2WithInvalidBytecode() {
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(user001Address, user001Key, 50000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(user001Address), testKey002, blockingStubFull));

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long devUcrLimitBefore = accountResource.getUcrLimit();
    long devUcrUsageBefore = accountResource.getUcrUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("before trigger, devUcrLimitBefore is " + Long.toString(devUcrLimitBefore));
    logger.info("before trigger, devUcrUsageBefore is " + Long.toString(devUcrUsageBefore));
    logger.info("before trigger, devBalanceBefore is " + Long.toString(devBalanceBefore));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitBefore = accountResource.getUcrLimit();
    long userUcrUsageBefore = accountResource.getUcrUsed();
    long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("before trigger, userUcrLimitBefore is " + Long.toString(userUcrLimitBefore));
    logger.info("before trigger, userUcrUsageBefore is " + Long.toString(userUcrUsageBefore));
    logger.info("before trigger, userBalanceBefore is " + Long.toString(userBalanceBefore));

    Long callValue = Long.valueOf(0);

    String testContractCode = "608060405234801561001057600080fd5b50d3801561001d57600080fd5b50d2801"
        + "561002a57600080fd5b5060c9806100396000396000f5fe6080604052348015600f57600080fd5b50d38015"
        + "601b57600080fd5b50d28015602757600080fd5b50600436106066577c01000000000000000000000000000"
        + "00000000000000000000000000000600035046368e5c0668114606b578063e5aa3d58146083575b600080fd"
        + "5b60716089565b60408051918252519081900360200190f35b60716097565b6000805460010190819055905"
        + "65b6000548156fea165627a7a72305820f3e3c0646a8c8d521fe819f10a592327469f611f0d9e8206697f7f"
        + "3436ff3c7d0029";

    Long salt = 1L;

    String param = "\"" + testContractCode + "\"," + salt;

    final String triggerTxid = PublicMethed.triggerContract(factoryContractAddress,
        "deploy(bytes,uint256)", param, false, callValue,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devUcrLimitAfter = accountResource.getUcrLimit();
    long devUcrUsageAfter = accountResource.getUcrUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("after trigger, devUcrLimitAfter is " + Long.toString(devUcrLimitAfter));
    logger.info("after trigger, devUcrUsageAfter is " + Long.toString(devUcrUsageAfter));
    logger.info("after trigger, devBalanceAfter is " + Long.toString(devBalanceAfter));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitAfter = accountResource.getUcrLimit();
    long userUcrUsageAfter = accountResource.getUcrUsed();
    long userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("after trigger, userUcrLimitAfter is " + Long.toString(userUcrLimitAfter));
    logger.info("after trigger, userUcrUsageAfter is " + Long.toString(userUcrUsageAfter));
    logger.info("after trigger, userBalanceAfter is " + Long.toString(userBalanceAfter));

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);

    TransactionInfo transactionInfo = infoById.get();
    logger.info("UcrUsageTotal: " + transactionInfo.getReceipt().getUcrUsageTotal());
    logger.info("NetUsage: " + transactionInfo.getReceipt().getNetUsage());

    Assert.assertEquals(1, infoById.get().getResultValue());
    Assert
        .assertThat(infoById.get().getResMessage().toStringUtf8(),
            containsString("Not enough ucr for 'SWAP1' operation executing"));
  }

  @Test(enabled = true, description = "Trigger create2 command with empty bytecode")
  public void test03TriggerCreate2WithEmptyBytecode() {
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(user001Address, user001Key, 50000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(user001Address), testKey002, blockingStubFull));

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long devUcrLimitBefore = accountResource.getUcrLimit();
    long devUcrUsageBefore = accountResource.getUcrUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("before trigger, devUcrLimitBefore is " + Long.toString(devUcrLimitBefore));
    logger.info("before trigger, devUcrUsageBefore is " + Long.toString(devUcrUsageBefore));
    logger.info("before trigger, devBalanceBefore is " + Long.toString(devBalanceBefore));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitBefore = accountResource.getUcrLimit();
    long userUcrUsageBefore = accountResource.getUcrUsed();
    long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("before trigger, userUcrLimitBefore is " + Long.toString(userUcrLimitBefore));
    logger.info("before trigger, userUcrUsageBefore is " + Long.toString(userUcrUsageBefore));
    logger.info("before trigger, userBalanceBefore is " + Long.toString(userBalanceBefore));

    Long callValue = Long.valueOf(0);

    String testContractCode = "";

    Long salt = 1L;

    String param = "\"" + testContractCode + "\"," + salt;

    final String triggerTxid = PublicMethed.triggerContract(factoryContractAddress,
        "deploy(bytes,uint256)", param, false, callValue,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devUcrLimitAfter = accountResource.getUcrLimit();
    long devUcrUsageAfter = accountResource.getUcrUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("after trigger, devUcrLimitAfter is " + Long.toString(devUcrLimitAfter));
    logger.info("after trigger, devUcrUsageAfter is " + Long.toString(devUcrUsageAfter));
    logger.info("after trigger, devBalanceAfter is " + Long.toString(devBalanceAfter));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitAfter = accountResource.getUcrLimit();
    long userUcrUsageAfter = accountResource.getUcrUsed();
    long userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("after trigger, userUcrLimitAfter is " + Long.toString(userUcrLimitAfter));
    logger.info("after trigger, userUcrUsageAfter is " + Long.toString(userUcrUsageAfter));
    logger.info("after trigger, userBalanceAfter is " + Long.toString(userBalanceAfter));

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);

    TransactionInfo transactionInfo = infoById.get();
    logger.info("UcrUsageTotal: " + transactionInfo.getReceipt().getUcrUsageTotal());
    logger.info("NetUsage: " + transactionInfo.getReceipt().getNetUsage());

    Assert.assertEquals(1, infoById.get().getResultValue());
    Assert
        .assertThat(infoById.get().getResMessage().toStringUtf8(),
            containsString("REVERT opcode executed"));
  }

  @Test(enabled = true, description = "Trigger create2 command with \"6080\" bytecode")
  public void test04TriggerCreate2WithShortBytecode() {
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(user001Address, user001Key, 50000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(user001Address), testKey002, blockingStubFull));

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long devUcrLimitBefore = accountResource.getUcrLimit();
    long devUcrUsageBefore = accountResource.getUcrUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("before trigger, devUcrLimitBefore is " + Long.toString(devUcrLimitBefore));
    logger.info("before trigger, devUcrUsageBefore is " + Long.toString(devUcrUsageBefore));
    logger.info("before trigger, devBalanceBefore is " + Long.toString(devBalanceBefore));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitBefore = accountResource.getUcrLimit();
    long userUcrUsageBefore = accountResource.getUcrUsed();
    long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("before trigger, userUcrLimitBefore is " + Long.toString(userUcrLimitBefore));
    logger.info("before trigger, userUcrUsageBefore is " + Long.toString(userUcrUsageBefore));
    logger.info("before trigger, userBalanceBefore is " + Long.toString(userBalanceBefore));

    Long callValue = Long.valueOf(0);

    String testContractCode = "6080";

    Long salt = 1L;

    String param = "\"" + testContractCode + "\"," + salt;

    final String triggerTxid = PublicMethed.triggerContract(factoryContractAddress,
        "deploy(bytes,uint256)", param, false, callValue,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devUcrLimitAfter = accountResource.getUcrLimit();
    long devUcrUsageAfter = accountResource.getUcrUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("after trigger, devUcrLimitAfter is " + Long.toString(devUcrLimitAfter));
    logger.info("after trigger, devUcrUsageAfter is " + Long.toString(devUcrUsageAfter));
    logger.info("after trigger, devBalanceAfter is " + Long.toString(devBalanceAfter));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitAfter = accountResource.getUcrLimit();
    long userUcrUsageAfter = accountResource.getUcrUsed();
    long userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("after trigger, userUcrLimitAfter is " + Long.toString(userUcrLimitAfter));
    logger.info("after trigger, userUcrUsageAfter is " + Long.toString(userUcrUsageAfter));
    logger.info("after trigger, userBalanceAfter is " + Long.toString(userBalanceAfter));

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);

    TransactionInfo transactionInfo = infoById.get();
    logger.info("UcrUsageTotal: " + transactionInfo.getReceipt().getUcrUsageTotal());
    logger.info("NetUsage: " + transactionInfo.getReceipt().getNetUsage());

    Assert.assertEquals(1, infoById.get().getResultValue());
    Assert
        .assertThat(infoById.get().getResMessage().toStringUtf8(),
            containsString("REVERT opcode executed"));
  }

  @Test(enabled = true, description = "Trigger create2 command with \"00000000000\" bytecode")
  public void test05TriggerCreate2WithZeroBytecode() {
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(user001Address, user001Key, 50000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(user001Address), testKey002, blockingStubFull));

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long devUcrLimitBefore = accountResource.getUcrLimit();
    long devUcrUsageBefore = accountResource.getUcrUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("before trigger, devUcrLimitBefore is " + Long.toString(devUcrLimitBefore));
    logger.info("before trigger, devUcrUsageBefore is " + Long.toString(devUcrUsageBefore));
    logger.info("before trigger, devBalanceBefore is " + Long.toString(devBalanceBefore));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitBefore = accountResource.getUcrLimit();
    long userUcrUsageBefore = accountResource.getUcrUsed();
    long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("before trigger, userUcrLimitBefore is " + Long.toString(userUcrLimitBefore));
    logger.info("before trigger, userUcrUsageBefore is " + Long.toString(userUcrUsageBefore));
    logger.info("before trigger, userBalanceBefore is " + Long.toString(userBalanceBefore));

    Long callValue = Long.valueOf(0);

    String testContractCode = "000000000000000000000000000000";

    Long salt = 1L;

    String param = "\"" + testContractCode + "\"," + salt;

    final String triggerTxid = PublicMethed.triggerContract(factoryContractAddress,
        "deploy(bytes,uint256)", param, false, callValue,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devUcrLimitAfter = accountResource.getUcrLimit();
    long devUcrUsageAfter = accountResource.getUcrUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("after trigger, devUcrLimitAfter is " + Long.toString(devUcrLimitAfter));
    logger.info("after trigger, devUcrUsageAfter is " + Long.toString(devUcrUsageAfter));
    logger.info("after trigger, devBalanceAfter is " + Long.toString(devBalanceAfter));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitAfter = accountResource.getUcrLimit();
    long userUcrUsageAfter = accountResource.getUcrUsed();
    long userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("after trigger, userUcrLimitAfter is " + Long.toString(userUcrLimitAfter));
    logger.info("after trigger, userUcrUsageAfter is " + Long.toString(userUcrUsageAfter));
    logger.info("after trigger, userBalanceAfter is " + Long.toString(userBalanceAfter));

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);

    TransactionInfo transactionInfo = infoById.get();
    logger.info("UcrUsageTotal: " + transactionInfo.getReceipt().getUcrUsageTotal());
    logger.info("NetUsage: " + transactionInfo.getReceipt().getNetUsage());

    Assert.assertEquals(1, infoById.get().getResultValue());
    Assert
        .assertThat(infoById.get().getResMessage().toStringUtf8(),
            containsString("REVERT opcode executed"));
  }

  @Test(enabled = true, description = "Trigger create2 command with NULL bytecode")
  public void test06TriggerCreate2WithNullBytecode() {
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(user001Address, user001Key, 50000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(user001Address), testKey002, blockingStubFull));

    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long devUcrLimitBefore = accountResource.getUcrLimit();
    long devUcrUsageBefore = accountResource.getUcrUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("before trigger, devUcrLimitBefore is " + Long.toString(devUcrLimitBefore));
    logger.info("before trigger, devUcrUsageBefore is " + Long.toString(devUcrUsageBefore));
    logger.info("before trigger, devBalanceBefore is " + Long.toString(devBalanceBefore));

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitBefore = accountResource.getUcrLimit();
    long userUcrUsageBefore = accountResource.getUcrUsed();
    long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("before trigger, userUcrLimitBefore is " + Long.toString(userUcrLimitBefore));
    logger.info("before trigger, userUcrUsageBefore is " + Long.toString(userUcrUsageBefore));
    logger.info("before trigger, userBalanceBefore is " + Long.toString(userBalanceBefore));

    Long callValue = Long.valueOf(0);

    String testContractCode = null;

    Long salt = 1L;

    String param = "\"" + testContractCode + "\"," + salt;
    boolean ret = false;

    try {
      final String triggerTxid = PublicMethed.triggerContract(factoryContractAddress,
          "deploy(bytes,uint256)", param, false, callValue,
          1000000000L, "0", 0, user001Address, user001Key,
          blockingStubFull);
    } catch (org.bouncycastle.util.encoders.DecoderException e) {
      logger.info("Expected org.bouncycastle.util.encoders.DecoderException!");
      ret = true;
    }
    Assert.assertTrue(ret);

    PublicMethed.unCdBalance(fromAddress, testKey002, 1,
        dev001Address, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 0,
        dev001Address, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 1,
        user001Address, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 0,
        user001Address, blockingStubFull);
  }


  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(user001Address, user001Key, fromAddress, blockingStubFull);
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 0, user001Address, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 0, dev001Address, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


