package stest.stabila.wallet.dailybuild.svmnewcommand.create2;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.List;
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
import stest.stabila.wallet.common.client.WalletClient;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class Create2Test025 {

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

  @Test(enabled = false, description = "Deploy factory contract")
  public void test01DeployFactoryContract() {
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 10000_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 10000_000_000L, fromAddress,
        testKey002, blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(dev001Address,
        blockingStubFull);
    long ucrLimit = accountResource.getUcrLimit();
    long ucrUsage = accountResource.getUcrUsed();
    long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    logger.info("before ucrLimit is " + Long.toString(ucrLimit));
    logger.info("before ucrUsage is " + Long.toString(ucrUsage));
    logger.info("before balanceBefore is " + Long.toString(balanceBefore));

    String filePath = "./src/test/resources/soliditycode/Create2Test025.sol";
    String contractName = "Factory";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    code += "05";
    final String transferTokenTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "",
            maxFeeLimit, 0L, 0, 10000,
            "0", 0, null, dev001Key,
            dev001Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    ucrLimit = accountResource.getUcrLimit();
    ucrUsage = accountResource.getUcrUsed();
    long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();

    logger.info("after ucrLimit is " + Long.toString(ucrLimit));
    logger.info("after ucrUsage is " + Long.toString(ucrUsage));
    logger.info("after balanceAfter is " + Long.toString(balanceAfter));

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

  @Test(enabled = false, description = "create2 bytecode with parm")
  public void test02TriggerTestContract() {
    //Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
    //    PublicMethed.getCdBalanceCount(user001Address, user001Key, 50000L,
    //        blockingStubFull), 0, 1,
    //    ByteString.copyFrom(user001Address), testKey002, blockingStubFull));

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

    String filePath = "./src/test/resources/soliditycode/Create2Test025.sol";
    String contractName = "TestContract";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String testContractCode = retMap.get("byteCode").toString();
    testContractCode += "0000000000000000000000000000000000000000000000000000000000000005";
    Long salt = 4L;

    String param = "\"" + testContractCode + "\"," + salt;

    String triggerTxid = null;
    triggerTxid = PublicMethed.triggerContract(factoryContractAddress,
        "create2(bytes,uint256)", param, false, 0L,
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

    logger.info(
        "the value: " + PublicMethed
            .getStrings(transactionInfo.getLogList().get(0).getData().toByteArray()));

    List<String> retList = PublicMethed
        .getStrings(transactionInfo.getLogList().get(0).getData().toByteArray());

    Long actualSalt = ByteArray.toLong(ByteArray.fromHexString(retList.get(1)));

    logger.info("actualSalt: " + actualSalt);

    byte[] tmpAddress = new byte[20];
    System.arraycopy(ByteArray.fromHexString(retList.get(0)), 12, tmpAddress, 0, 20);
    String addressHex = "41" + ByteArray.toHexString(tmpAddress);
    logger.info("address_hex: " + addressHex);
    String addressFinal = Base58.encode58Check(ByteArray.fromHexString(addressHex));
    logger.info("address_final: " + addressFinal);

    testContractAddress = WalletClient.decodeFromBase58Check(addressFinal);

    String txid = PublicMethed.triggerContract(testContractAddress,
        "getNum()", "#", false, 0L,
        1000000000L, "0", 0, user001Address, user001Key,
        blockingStubFull);
    Optional<TransactionInfo> infoById2 = PublicMethed.getTransactionInfoById(txid,
        blockingStubFull);
    TransactionInfo transactionInfo2 = infoById2.get();
    final int Num = ByteArray.toInt(transactionInfo2.getContractResult(0).toByteArray());

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    userUcrLimitAfter = accountResource.getUcrLimit();
    userUcrUsageAfter = accountResource.getUcrUsed();
    userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("UcrUsageTotal: " + transactionInfo.getReceipt().getUcrUsageTotal());
    logger.info("NetUsage: " + transactionInfo.getReceipt().getNetUsage());
    logger.info("after trigger, userBalanceAfter is " + Long.toString(userBalanceAfter));

    logger.info("NUM :" + Num);
    Assert.assertEquals(infoById.get().getResult().toString(), "SUCESS");
    Assert.assertEquals(infoById.get().getResultValue(), 0);
    Assert.assertEquals(5, Num);


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


