package stest.stabila.wallet.dailybuild.svmnewcommand.extCodeHash;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI.AccountResourceMessage;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.runtime.vm.DataWord;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ExtCodeHashTest005 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  private byte[] extCodeHashContractAddress = null;
  private byte[] testContractAddress = null;

  private String contractCodeHash = null;

  private ECKey ecKey1 = new ECKey(Utils.getRandom());
  private byte[] dev001Address = ecKey1.getAddress();
  private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  private ECKey ecKey2 = new ECKey(Utils.getRandom());
  private byte[] user001Address = ecKey2.getAddress();
  private String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  private ECKey ecKey3 = new ECKey(Utils.getRandom());
  private byte[] testAddress = ecKey3.getAddress();
  private String testKey = ByteArray.toHexString(ecKey3.getPrivKeyBytes());

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

    String fakeAddress = "";

    logger.info("realAddress: " + fakeAddress);
    byte[] fullHexAddr = new DataWord(fakeAddress).getData();
    logger.info("fullHexAddr  ++=  " + Hex.toHexString(fullHexAddr));

    fakeAddress = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

    logger.info("realAddress: " + fakeAddress);
    fullHexAddr = new DataWord(fakeAddress).getData();
    logger.info("fullHexAddr  ++=  " + Hex.toHexString(fullHexAddr));

  }

  @Test(enabled = true, description = "Deploy extcodehash contract")
  public void test01DeployExtCodeHashContract() {
    Assert.assertTrue(PublicMethed.sendcoin(dev001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(dev001Address, dev001Key, 170000L,
            blockingStubFull), 0, 1,
        ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress, 10_000_000L,
        0, 0, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));

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

    String filePath = "./src/test/resources/soliditycode/extCodeHash.sol";
    String contractName = "TestExtCodeHash";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

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

    extCodeHashContractAddress = infoById.get().getContractAddress().toByteArray();
    SmartContract smartContract = PublicMethed.getContract(extCodeHashContractAddress,
        blockingStubFull);
    Assert.assertNotNull(smartContract.getAbi());
  }

  @Test(enabled = true, description = "Get codehash of a real contract by uint")
  public void test02GetContractCodeHash() {

    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

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

    String testAddress = ByteArray.toHexString(extCodeHashContractAddress);
    logger.info("realAddress: " + testAddress);
    byte[] fullHexAddr = new DataWord(testAddress).getData();

    final String triggerTxid = PublicMethed.triggerContract(extCodeHashContractAddress,
        "getCodeHashByUint(uint256)", Hex.toHexString(fullHexAddr), true, callValue,
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

    if (infoById.get().getResultValue() != 0) {
      Assert.fail(
          "transaction failed with message: " + infoById.get().getResMessage().toStringUtf8());
    }

    List<String> retList = PublicMethed
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info("the value: " + retList);

    Assert.assertFalse(retList.isEmpty());
    Assert.assertNotEquals("C5D2460186F7233C927E7DB2DCC703C0E500B653CA82273B7BFAD8045D85A470",
        retList.get(0));
    Assert.assertNotEquals("0000000000000000000000000000000000000000000000000000000000000000",
        retList.get(0));

    contractCodeHash = retList.get(0);

    SmartContract smartContract = PublicMethed
        .getContract(extCodeHashContractAddress, blockingStubFull);
    logger.info(smartContract.getBytecode().toStringUtf8());
  }

  @Test(enabled = true, description = "Get codehash of a fake address by uint")
  public void test03GetInvalidAddressCodeHash() {
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

    String fakeAddress = "41660757B2543F4849D3F42B90F58DE1C14C7E0038";
    logger.info("realAddress: " + fakeAddress);
    byte[] fullHexAddr = new DataWord(fakeAddress).getData();

    final String triggerTxid = PublicMethed.triggerContract(extCodeHashContractAddress,
        "getCodeHashByUint(uint256)", Hex.toHexString(fullHexAddr), true, callValue,
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

    if (infoById.get().getResultValue() != 0) {
      Assert.fail(
          "transaction failed with message: " + infoById.get().getResMessage().toStringUtf8());
    }

    List<String> retList = PublicMethed
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info("the value: " + retList);

    Assert.assertFalse(retList.isEmpty());

    Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
        retList.get(0));

    SmartContract smartContract = PublicMethed
        .getContract(extCodeHashContractAddress, blockingStubFull);
    logger.info(smartContract.getBytecode().toStringUtf8());
  }

  @Test(enabled = true, description = "Get codehash of a normal account by uint")
  public void test04GetNormalAddressCodeHash() {
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

    String fakeAddress = ByteArray.toHexString(user001Address);
    logger.info("realAddress: " + fakeAddress);
    byte[] fullHexAddr = new DataWord(fakeAddress).getData();

    final String triggerTxid = PublicMethed.triggerContract(extCodeHashContractAddress,
        "getCodeHashByUint(uint256)", Hex.toHexString(fullHexAddr), true, callValue,
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

    if (infoById.get().getResultValue() != 0) {
      Assert.fail(
          "transaction failed with message: " + infoById.get().getResMessage().toStringUtf8());
    }

    List<String> retList = PublicMethed
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info("the value: " + retList);
    Assert.assertFalse(retList.isEmpty());

    Assert.assertEquals("C5D2460186F7233C927E7DB2DCC703C0E500B653CA82273B7BFAD8045D85A470",
        retList.get(0));

    SmartContract smartContract = PublicMethed
        .getContract(extCodeHashContractAddress, blockingStubFull);
    logger.info(smartContract.getBytecode().toStringUtf8());
  }

  @Test(enabled = true, description = "Get codehash of a empty address by uint")
  public void test05GetEmptyAddressCodeHash() {
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

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

    String fakeAddress = "";

    logger.info("realAddress: " + fakeAddress);
    byte[] fullHexAddr = new DataWord(fakeAddress).getData();
    logger.info("fullHexAddr  ++=  " + Hex.toHexString(fullHexAddr));

    final String triggerTxid = PublicMethed.triggerContract(extCodeHashContractAddress,
        "getCodeHashByUint(uint256)", Hex.toHexString(fullHexAddr), true, callValue,
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

    if (infoById.get().getResultValue() != 0) {
      Assert.fail(
          "transaction failed with message: " + infoById.get().getResMessage().toStringUtf8());
    }

    List<String> retList = PublicMethed
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info("the value: " + retList);
    Assert.assertFalse(retList.isEmpty());

    Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
        retList.get(0));

    SmartContract smartContract = PublicMethed
        .getContract(extCodeHashContractAddress, blockingStubFull);
    logger.info(smartContract.getBytecode().toStringUtf8());
  }

  @Test(enabled = true, description = "Get codehash of a fffffff*64 address by uint")
  public void test06GetFakeAddressCodeHash() {
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

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

    String fakeAddress = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

    logger.info("realAddress: " + fakeAddress);
    byte[] fullHexAddr = new DataWord(fakeAddress).getData();
    logger.info("fullHexAddr  ++=  " + Hex.toHexString(fullHexAddr));

    final String triggerTxid = PublicMethed.triggerContract(extCodeHashContractAddress,
        "getCodeHashByUint(uint256)", Hex.toHexString(fullHexAddr), true, callValue,
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

    if (infoById.get().getResultValue() != 0) {
      Assert.fail(
          "transaction failed with message: " + infoById.get().getResMessage().toStringUtf8());
    }

    List<String> retList = PublicMethed
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info("the value: " + retList);
    Assert.assertFalse(retList.isEmpty());

    Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
        retList.get(0));

    SmartContract smartContract = PublicMethed
        .getContract(extCodeHashContractAddress, blockingStubFull);
    logger.info(smartContract.getBytecode().toStringUtf8());
  }

  @Test(enabled = true, description = "Get codehash of a real contract plus 2**160 by uint")
  public void test07GetContractAddress96CodeHash() {
    Assert.assertTrue(PublicMethed.sendcoin(user001Address, 100_000_000L, fromAddress,
        testKey002, blockingStubFull));

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

    BigInteger bigIntAddr = new DataWord(extCodeHashContractAddress).sValue();
    String bigIntAddrChange = BigInteger.valueOf(2).pow(160).add(bigIntAddr).toString(16);
    byte[] fullHexAddr = new DataWord(bigIntAddrChange).getData();

    final String triggerTxid = PublicMethed.triggerContract(extCodeHashContractAddress,
        "getCodeHashByUint(uint256)", Hex.toHexString(fullHexAddr), true, callValue,
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

    if (infoById.get().getResultValue() != 0) {
      Assert.fail(
          "transaction failed with message: " + infoById.get().getResMessage().toStringUtf8());
    }

    List<String> retList = PublicMethed
        .getStrings(transactionInfo.getContractResult(0).toByteArray());

    logger.info("the value: " + retList);
    Assert.assertFalse(retList.isEmpty());

    // expect the code hash same
    Assert.assertEquals(contractCodeHash, retList.get(0));

    SmartContract smartContract = PublicMethed
        .getContract(extCodeHashContractAddress, blockingStubFull);
    logger.info(smartContract.getBytecode().toStringUtf8());

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


