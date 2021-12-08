package stest.stabila.wallet.dailybuild.svmnewcommand.transferfailed;

import static org.stabila.protos.Protocol.TransactionInfo.code.FAILED;

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
import org.stabila.core.vm.UcrCost;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Transaction.Result.contractResult;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.WalletClient;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class TransferFailed001 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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

  @Test(enabled = true, description = "Transfer stb insufficient balance")
  public void test001TransferStbInsufficientBalance() {
    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 10000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "src/test/resources/soliditycode/TransferFailed001.sol";
    String contractName = "UcrOfTransferFailedTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        2000000L, 100, null, contractExcKey,
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
    String txid = "";
    String num = "2000001";
    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbInsufficientBalance(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("infoById:" + infoById);
    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.REVERT, infoById.get().getReceipt().getResult());
    Assert.assertEquals(
        "REVERT opcode executed",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, ucrUsageTotal);


  }


  @Test(enabled = true, description = "Transfer balance enough")
  public void test002TransferEnough() {

    //Assert.assertTrue(PublicMethed
    //    .sendcoin(contractAddress, 3000000L, testNetAccountAddress, testNetAccountKey,
    //        blockingStubFull));
    //PublicMethed.waitProduceNextBlock(blockingStubFull);
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
    String txid = "";
    String num = "1";
    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbInsufficientBalance(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    logger.info("infoById" + infoById);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee - 1 == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);


  }


  @Test(enabled = true, description = "Transfer stb nonexistent target")
  public void test003TransferStbNonexistentTarget() {

    //Assert.assertTrue(PublicMethed
    //    .sendcoin(contractAddress, 1000000L, testNetAccountAddress, testNetAccountKey,
    //        blockingStubFull));

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
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] nonexistentAddress = ecKey2.getAddress();
    String txid = "";
    String num = "1" + ",\"" + Base58.encode58Check(nonexistentAddress) + "\"";

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbNonexistentTarget(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Account nonexistentAddressAccount = PublicMethed
        .queryAccount(nonexistentAddress, blockingStubFull1);
    Assert.assertEquals(1, nonexistentAddressAccount.getBalance());
    Assert.assertEquals(0, infoById.get().getResultValue());

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, ucrUsageTotal);

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbNonexistentTarget(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

    fee = infoById.get().getFee();
    netUsed = infoById.get().getReceipt().getNetUsage();
    ucrUsed = infoById.get().getReceipt().getUcrUsage();
    netFee = infoById.get().getReceipt().getNetFee();
    long ucrUsageTotal2 = infoById.get().getReceipt().getUcrUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("ucrUsed:" + ucrUsed);
    logger.info("netFee:" + netFee);
    logger.info("ucrUsageTotal:" + ucrUsageTotal2);

    infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    afterBalance = infoafter.getBalance();
    afterUcrUsed = resourceInfoafter.getUcrUsed();
    afterNetUsed = resourceInfoafter.getNetUsed();
    afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    nonexistentAddressAccount = PublicMethed
        .queryAccount(nonexistentAddress, blockingStubFull1);
    Assert.assertEquals(2, nonexistentAddressAccount.getBalance());
    Assert.assertEquals(0, infoById.get().getResultValue());

    Assert.assertEquals(ucrUsageTotal2 + UcrCost.getInstance().getNewAcctCall(),
        ucrUsageTotal);

  }


  @Test(enabled = true, description = "Transfer stb to myself")
  public void test004TransferStbSelf() {

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
    String txid = "";
    String num = "1";

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbSelf(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.TRANSFER_FAILED, infoById.get().getReceipt().getResult());
    Assert.assertEquals("transfer stb failed: Cannot transfer STB to yourself.",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, ucrUsageTotal);


  }


  @Test(enabled = true, description = "Transfer stb nonexistent target and insufficient balance")
  public void test005TransferStbNonexistentTarget() {

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
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] nonexistentAddress = ecKey2.getAddress();
    String txid = "";
    String num = "10000000" + ",\"" + Base58.encode58Check(nonexistentAddress) + "\"";

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbNonexistentTarget(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.REVERT, infoById.get().getReceipt().getResult());
    Assert.assertEquals(
        "REVERT opcode executed",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, ucrUsageTotal);


  }


  @Test(enabled = true, description = "Transfer stb to myself and insufficient balance")
  public void test006TransferStbSelf() {

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
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    String txid = "";
    String num = "1000000000";

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbSelf(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 1);
    Assert.assertEquals(contractResult.REVERT, infoById.get().getReceipt().getResult());
    Assert.assertEquals(
        "REVERT opcode executed",
        ByteArray.toStr(infoById.get().getResMessage().toByteArray()));

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertNotEquals(10000000, ucrUsageTotal);


  }

  @Test(enabled = true, description = "PreCompiled transfertoken with value,"
      + " long.max < value or long.min > value")
  public void test007TransferTrckenPreCompiled() {

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    Account info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    String txid = "";
    String num = "1";

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferTokenCompiledLongMax1()", "#", false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
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

    resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    beforeBalance = info.getBalance();
    beforeUcrUsed = resourceInfo.getUcrUsed();
    beforeNetUsed = resourceInfo.getNetUsed();
    beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    Assert.assertEquals(FAILED, infoById.get().getResult());
    Assert.assertTrue(ucrUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferTokenCompiledLongMin1()", "#", false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    fee = infoById.get().getFee();
    netUsed = infoById.get().getReceipt().getNetUsage();
    ucrUsed = infoById.get().getReceipt().getUcrUsage();
    netFee = infoById.get().getReceipt().getNetFee();
    ucrUsageTotal = infoById.get().getReceipt().getUcrUsageTotal();
    logger.info("fee:" + fee);
    logger.info("netUsed:" + netUsed);
    logger.info("ucrUsed:" + ucrUsed);
    logger.info("netFee:" + netFee);
    logger.info("ucrUsageTotal:" + ucrUsageTotal);

    resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    beforeBalance = info.getBalance();
    beforeUcrUsed = resourceInfo.getUcrUsed();
    beforeNetUsed = resourceInfo.getNetUsed();
    beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    Assert.assertEquals(FAILED, infoById.get().getResult());
    Assert.assertTrue(ucrUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals("REVERT opcode executed", infoById.get().getResMessage().toStringUtf8());

  }

  @Test(enabled = false, description = "PreCompiled tokenbalance")
  public void test008TransferTrctoken() {

    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 10000_000_000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    String description = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetDescription");
    String url = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetUrl");

    ByteString assetAccountId = null;
    final long TotalSupply = 10000000L;
    long now = System.currentTimeMillis();
    String tokenName = "testAssetIssue_" + Long.toString(now);

    //Create a new AssetIssue success.
    Assert
        .assertTrue(PublicMethed.createAssetIssue(contractExcAddress, tokenName, TotalSupply, 1,
            10000, start, end, 1, description, url, 100000L,
            100000L, 1L, 1L, contractExcKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    assetAccountId = PublicMethed.queryAccount(contractExcAddress, blockingStubFull)
        .getAssetIssuedID();

    String filePath = "src/test/resources/soliditycode/TransferFailed001.sol";
    String contractName = "UcrOfTransferFailedTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.transferAsset(contractAddress,
        assetAccountId.toByteArray(), 100L, contractExcAddress, contractExcKey,
        blockingStubFull));

    Long returnAddressBytesAccountCountBefore = PublicMethed
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("returnAddressBytesAccountCountBefore : " + returnAddressBytesAccountCountBefore);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    Long testNetAccountCountBefore = PublicMethed
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountBefore = PublicMethed
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("testNetAccountCountBefore:" + testNetAccountCountBefore);
    logger.info("contractAccountCountBefore:" + contractAccountCountBefore);
    String txid = "";
    String num =
        "\"" + Base58.encode58Check(contractAddress) + "\"," + "\"" + assetAccountId.toStringUtf8()
            + "\"";
    //String num = "\""+Base58.encode58Check(contractAddress) +"\","+ "\"" + -1 + "\"";
    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferTokenTest(address,uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    Long testNetAccountCountAfter = PublicMethed
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountAfter = PublicMethed
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("testNetAccountCountAfter:" + testNetAccountCountAfter);
    logger.info("contractAccountCountAfter:" + contractAccountCountAfter);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(ucrUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals(100, ByteArray.toInt(infoById.get().getContractResult(0).toByteArray()));


  }

  @Test(enabled = true, description = "PreCompiled address(0x1) query tokenbalance")
  public void test009TransferTrctoken() {
    //address: 410000000000000000000000000000000000000001
    String addressx = "T9yD14Nj9j7xAB4dbGeiX9h8unkKLxmGkn";
    byte[] addressxx = WalletClient.decodeFromBase58Check(addressx);

    Assert.assertTrue(PublicMethed
        .sendcoin(addressxx, 1000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    String description = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetDescription");
    String url = Configuration.getByPath("testng.conf")
        .getString("defaultParameter.assetUrl");

    ByteString assetAccountId = null;
    final long TotalSupply = 10000000L;
    long now = System.currentTimeMillis();
    String tokenName = "testAssetIssue_" + Long.toString(now);

    //Create a new AssetIssue success.
    Assert
        .assertTrue(PublicMethed.createAssetIssue(contractExcAddress, tokenName, TotalSupply, 1,
            10000, start, end, 1, description, url, 100000L,
            100000L, 1L, 1L, contractExcKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    assetAccountId = PublicMethed.queryAccount(contractExcAddress, blockingStubFull)
        .getAssetIssuedID();

    Assert.assertTrue(PublicMethed.transferAsset(addressxx,
        assetAccountId.toByteArray(), 100L, contractExcAddress, contractExcKey,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long returnAddressBytesAccountCountBefore = PublicMethed
        .getAssetIssueValue(addressxx, assetAccountId, blockingStubFull);
    logger.info("returnAddressBytesAccountCountBefore : " + returnAddressBytesAccountCountBefore);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    Long testNetAccountCountBefore = PublicMethed
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountBefore = PublicMethed
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("testNetAccountCountBefore:" + testNetAccountCountBefore);
    logger.info("contractAccountCountBefore:" + contractAccountCountBefore);
    String txid = "";
    //String num = "\""+Base58.encode58Check(contractAddress)
    // +"\","+ "\"" + assetAccountId.toStringUtf8() + "\"";
    String num = "\"" + assetAccountId.toStringUtf8() + "\"";
    //String num = "\""+Base58.encode58Check(contractAddress) +"\","+ "\"" + -1 + "\"";
    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferTokenCompiledTokenId(uint256)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    Long testNetAccountCountAfter = PublicMethed
        .getAssetIssueValue(contractExcAddress, assetAccountId, blockingStubFull);
    Long contractAccountCountAfter = PublicMethed
        .getAssetIssueValue(contractAddress, assetAccountId, blockingStubFull);
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("testNetAccountCountAfter:" + testNetAccountCountAfter);
    logger.info("contractAccountCountAfter:" + contractAccountCountAfter);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(ucrUsageTotal < maxFeeLimit / 10);
    Assert.assertEquals(100, ByteArray.toInt(infoById.get().getContractResult(0).toByteArray()));
  }

  @Test(enabled = true, description = "transferStb to nonexistent target ,but revert")
  public void test010TransferRevert() {

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
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] nonexistentAddress = ecKey2.getAddress();
    String txid = "";
    String num = "1" + ",\"" + Base58.encode58Check(nonexistentAddress) + "\"";

    txid = PublicMethed.triggerContract(contractAddress,
        "testTransferStbrevert(uint256,address)", num, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);

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

    Account infoafter = PublicMethed.queryAccount(contractExcKey, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(contractExcAddress,
        blockingStubFull1);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Account nonexistentAddressAccount = PublicMethed
        .queryAccount(nonexistentAddress, blockingStubFull1);
    Assert.assertEquals(0, nonexistentAddressAccount.getBalance());
    Assert.assertEquals(1, infoById.get().getResultValue());

    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    Assert.assertTrue(ucrUsageTotal > UcrCost.getInstance().getNewAcctCall());
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed
        .freedResource(contractExcAddress, contractExcKey, testNetAccountAddress, blockingStubFull);
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
