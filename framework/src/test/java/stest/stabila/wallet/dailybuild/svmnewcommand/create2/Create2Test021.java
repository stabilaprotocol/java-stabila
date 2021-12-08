package stest.stabila.wallet.dailybuild.svmnewcommand.create2;

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
import org.stabila.api.WalletSolidityGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class Create2Test021 {

  private static final long now = System.currentTimeMillis();
  private static final String name = "Asset008_" + Long.toString(now);
  private static final long totalSupply = now;
  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] bytes;
  String description = "just-test";
  String url = "https://github.com/stabilaprotocol/wallet-cli/";
  ByteString assetAccountId = null;
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] resourceOnwerAddress = ecKey2.getAddress();
  String resourceOnwerKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelSolidity = null;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

  ECKey ecKey3 = new ECKey(Utils.getRandom());
  private byte[] contractExcAddress = ecKey3.getAddress();
  private String contractExcKey = ByteArray.toHexString(ecKey3.getPrivKeyBytes());

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
    PublicMethed.printAddress(contractExcKey);
    PublicMethed.printAddress(resourceOnwerKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);

  }

  @Test(enabled = true, description = "resource delegate with create2 contract, and suicide ")
  public void test1TriggerContract() {
    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 10000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    Assert.assertTrue(PublicMethed
        .sendcoin(resourceOnwerAddress, 1000000000L + 1024000000L, testNetAccountAddress,
            testNetAccountKey,
            blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Create 3 the same name token.
    Long start = System.currentTimeMillis() + 2000;
    Long end = System.currentTimeMillis() + 1000000000;
    Assert.assertTrue(PublicMethed.createAssetIssue(resourceOnwerAddress,
        name, totalSupply, 1, 1, start, end, 1, description, url,
        2000L, 2000L, 1L, 1L, resourceOnwerKey, blockingStubFull));
    String filePath = "src/test/resources/soliditycode/create2contractn.sol";
    String contractName = "Factory";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);


    Long beforeExcAccountBalance = PublicMethed
        .queryAccount(resourceOnwerAddress, blockingStubFull).getBalance();
    //  create2 TestContract
    String contractName1 = "TestConstract";
    HashMap retMap1 = PublicMethed.getBycodeAbi(filePath, contractName1);
    String code1 = retMap1.get("byteCode").toString();
    String txid = "";
    String num = "\"" + code1 + "\"" + "," + 1;
    txid = PublicMethed
        .triggerContract(contractAddress,
            "deploy(bytes,uint256)", num, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee = infoById.get().getFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long ucrUsed = infoById.get().getReceipt().getUcrUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    long ucrUsageTotal = infoById.get().getReceipt().getUcrUsageTotal();

    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("ucrUsed:" + ucrUsed);
    logger.info("netFee:" + netFee);
    logger.info("ucrUsageTotal:" + ucrUsageTotal);

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 0);

    byte[] returnAddressBytes = infoById.get().getInternalTransactions(0).getTransferToAddress()
        .toByteArray();
    String returnAddress = Base58.encode58Check(returnAddressBytes);
    logger.info("returnAddress:" + returnAddress);

    bytes = returnAddressBytes;


    // cdBalanceForReceiver to create2 contract Address, transaction Failed

    Assert.assertFalse(PublicMethed.cdBalanceForReceiver(resourceOnwerAddress, 5000000L, 0, 0,
        ByteString.copyFrom(bytes), resourceOnwerKey, blockingStubFull));
    Assert.assertFalse(PublicMethed.cdBalanceForReceiver(resourceOnwerAddress, 5000000L, 0, 1,
        ByteString.copyFrom(bytes), resourceOnwerKey, blockingStubFull));
    Long afterExcAccountBalance = PublicMethed.queryAccount(resourceOnwerAddress, blockingStubFull)
        .getBalance();
    Assert.assertTrue(PublicMethed.getAccountResource(bytes, blockingStubFull).getNetLimit() == 0);
    Assert
        .assertTrue(PublicMethed.getAccountResource(bytes, blockingStubFull).getUcrLimit() == 0);
    logger.info("afterExcAccountBalance: " + afterExcAccountBalance);
    logger.info("beforeExcAccountBalance:" + beforeExcAccountBalance);

    Assert.assertTrue(afterExcAccountBalance - beforeExcAccountBalance == 0);


    // create2 Address Suicide
    String param2 = "\"" + Base58.encode58Check(contractExcAddress) + "\"";
    String txidn = PublicMethed
        .triggerContract(bytes,
            "testSuicideNonexistentTarget(address)", param2, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);


    // active create2 Address to normal Address
    Assert.assertTrue(PublicMethed
        .sendcoin(bytes, 1000000L, contractExcAddress, contractExcKey, blockingStubFull));
    //Trigger contract to transfer stb and token.
    Account getAssetIdFromAccount = PublicMethed
        .queryAccount(resourceOnwerAddress, blockingStubFull);
    assetAccountId = getAssetIdFromAccount.getAssetIssuedID();
    Long contractBeforeBalance = PublicMethed.queryAccount(bytes, blockingStubFull).getBalance();

    Assert.assertTrue(
        PublicMethed.transferAsset(bytes, assetAccountId.toByteArray(), 100, resourceOnwerAddress,
            resourceOnwerKey,
            blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account account1 = PublicMethed.queryAccount(bytes, blockingStubFull);
    int typeValue1 = account1.getTypeValue();
    Assert.assertEquals(0, typeValue1);

    // cdBalanceForReceiver to "create2" contract Address, transaction SUCCESS
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(resourceOnwerAddress, 1000000L, 0, 0,
        ByteString.copyFrom(bytes), resourceOnwerKey, blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(resourceOnwerAddress, 1000000L, 0, 1,
        ByteString.copyFrom(bytes), resourceOnwerKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    beforeExcAccountBalance = PublicMethed.queryAccount(resourceOnwerAddress, blockingStubFull)
        .getBalance();

    Assert.assertTrue(PublicMethed.unCdBalance(resourceOnwerAddress, resourceOnwerKey,
        0, bytes, blockingStubFull));
    Assert.assertTrue(PublicMethed.unCdBalance(resourceOnwerAddress, resourceOnwerKey,
        1, bytes, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long afterUncdBalance = PublicMethed.queryAccount(resourceOnwerAddress, blockingStubFull)
        .getBalance();
    Assert.assertTrue(afterUncdBalance == beforeExcAccountBalance + 1000000L * 2);


    // create2 TestContract to turn AccountType To create2 Contract Address
    txid = PublicMethed
        .triggerContract(contractAddress,
            "deploy(bytes,uint256)", num, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);

    // triggercontract Create2 address, function normal
    txid = PublicMethed
        .triggerContract(returnAddressBytes,
            "i()", "#", false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById1 = null;
    infoById1 = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Long fee1 = infoById1.get().getFee();
    Long netUsed1 = infoById1.get().getReceipt().getNetUsage();
    Long ucrUsed1 = infoById1.get().getReceipt().getUcrUsage();
    Long netFee1 = infoById1.get().getReceipt().getNetFee();
    long ucrUsageTotal1 = infoById1.get().getReceipt().getUcrUsageTotal();

    logger.info("fee1:" + fee1);
    logger.info("netUsed1:" + netUsed1);
    logger.info("ucrUsed1:" + ucrUsed1);
    logger.info("netFee1:" + netFee1);
    logger.info("ucrUsageTotal1:" + ucrUsageTotal1);

    Account infoafter1 = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    AccountResourceMessage resourceInfoafter1 = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    Long afterBalance1 = infoafter1.getBalance();
    Long afterUcrUsed1 = resourceInfoafter1.getUcrUsed();
    Long afterNetUsed1 = resourceInfoafter1.getNetUsed();
    Long afterFreeNetUsed1 = resourceInfoafter1.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance1);
    logger.info("afterUcrUsed:" + afterUcrUsed1);
    logger.info("afterNetUsed:" + afterNetUsed1);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed1);

    Assert.assertTrue(infoById1.get().getResultValue() == 0);
    Long returnnumber = ByteArray.toLong(ByteArray
        .fromHexString(ByteArray.toHexString(infoById1.get().getContractResult(0).toByteArray())));
    Assert.assertTrue(1 == returnnumber);
    Account account = PublicMethed.queryAccount(returnAddressBytes, blockingStubFull);
    int typeValue = account.getTypeValue();
    Assert.assertEquals(2, typeValue);
    Assert.assertEquals(account.getBalance(), 1000000);
  }

  @Test(enabled = true, description = "Create2 contract can transfer stb and token.")
  public void test2TriggerContract() {
    Account accountbefore = PublicMethed.queryAccount(bytes, blockingStubFull);
    int typeValue = accountbefore.getTypeValue();
    Assert.assertEquals(2, typeValue);
    long accountbeforeBalance = accountbefore.getBalance();
    Assert.assertEquals(accountbeforeBalance, 1000000);
    Account contractExcAddressbefore = PublicMethed
        .queryAccount(contractExcAddress, blockingStubFull);
    long contractExcAddressbeforeBalance = contractExcAddressbefore.getBalance();

    String num = "1";

    String txid = PublicMethed
        .triggerContract(bytes,
            "testTransfer(uint256)", num, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> transactionInfoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(transactionInfoById.get().getResultValue() == 0);
    Long fee1 = transactionInfoById.get().getFee();

    Account accountafter = PublicMethed.queryAccount(bytes, blockingStubFull);
    long accountafterBalance = accountafter.getBalance();
    Assert.assertTrue(accountbeforeBalance - 1 == accountafterBalance);

    Account contractExcAddressafter = PublicMethed
        .queryAccount(contractExcAddress, blockingStubFull);
    long contractExcAddressafterBalance = contractExcAddressafter.getBalance();
    Assert.assertTrue(contractExcAddressbeforeBalance + 1 - fee1 == contractExcAddressafterBalance);

    num = "1" + ",\"" + assetAccountId.toStringUtf8() + "\"";
    Long returnAddressBytesAccountCountBefore = PublicMethed
        .getAssetIssueValue(bytes, assetAccountId, blockingStubFull);
    Long contractExcAddressAccountCountBefore = PublicMethed
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    String txid1 = PublicMethed
        .triggerContract(bytes,
            "testTransferToken(uint256,trcToken)", num, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> transactionInfoById1 = PublicMethed
        .getTransactionInfoById(txid1, blockingStubFull);
    Assert.assertTrue(transactionInfoById1.get().getResultValue() == 0);
    Long returnAddressBytesAccountCountAfter = PublicMethed
        .getAssetIssueValue(bytes, assetAccountId, blockingStubFull);

    Long contractExcAddressAccountCountAfter = PublicMethed
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Assert.assertTrue(
        returnAddressBytesAccountCountBefore - 1 == returnAddressBytesAccountCountAfter);
    Assert.assertTrue(
        contractExcAddressAccountCountBefore + 1 == contractExcAddressAccountCountAfter);
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {

    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}