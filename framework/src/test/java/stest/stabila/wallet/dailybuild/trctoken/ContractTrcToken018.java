package stest.stabila.wallet.dailybuild.trctoken;

import static org.stabila.protos.Protocol.TransactionInfo.code.SUCESS;

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
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;


@Slf4j
public class ContractTrcToken018 {

  private static final long now = System.currentTimeMillis();
  private static final long TotalSupply = 1000L;
  private static String tokenName = "testAssetIssue_" + Long.toString(now);
  private static ByteString assetAccountId = null;
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private byte[] transferTokenContractAddress = null;
  private byte[] receiveTokenContractAddress = null;

  private String description = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetDescription");
  private String url = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetUrl");

  private ECKey ecKey1 = new ECKey(Utils.getRandom());
  private byte[] dev001Address = ecKey1.getAddress();
  private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  private ECKey ecKey2 = new ECKey(Utils.getRandom());
  private byte[] user001Address = ecKey2.getAddress();
  private String user001Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  private ECKey ecKey3 = new ECKey(Utils.getRandom());
  private byte[] tmpAddress = ecKey3.getAddress();
  private String tmp001Key = ByteArray.toHexString(ecKey3.getPrivKeyBytes());

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

    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    PublicMethed.printAddress(dev001Key);
    PublicMethed.printAddress(user001Key);
  }

  @Test(enabled = true, description = "Transfer token to an inactive account")
  public void testDeployTransferTokenContract() {
    Assert.assertTrue(PublicMethed
        .sendcoin(dev001Address, 1100_000_000L, fromAddress, testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed
        .sendcoin(user001Address, 100_000_000L, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(dev001Address, dev001Key, 50000L, blockingStubFull), 0,
        1, ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress, 10_000_000L, 0, 0,
        ByteString.copyFrom(dev001Address), testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long start = System.currentTimeMillis() + 2000;
    long end = System.currentTimeMillis() + 1000000000;
    //Create a new AssetIssue success.
    Assert.assertTrue(PublicMethed
        .createAssetIssue(dev001Address, tokenName, TotalSupply, 1, 10000, start, end, 1,
            description, url, 100000L, 100000L, 1L, 1L, dev001Key, blockingStubFull));
    assetAccountId = PublicMethed.queryAccount(dev001Address, blockingStubFull).getAssetIssuedID();
    logger.info("The token name: " + tokenName);
    logger.info("The token ID: " + assetAccountId.toStringUtf8());

    //before deploy, check account resource
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(dev001Address, blockingStubFull);
    long ucrLimit = accountResource.getUcrLimit();
    long ucrUsage = accountResource.getUcrUsed();
    long balanceBefore = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountBefore = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

    logger.info("before ucrLimit is " + ucrLimit);
    logger.info("before ucrUsage is " + ucrUsage);
    logger.info("before balanceBefore is " + balanceBefore);
    logger.info("before AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountBefore: "
        + devAssetCountBefore);

    String filePath = "./src/test/resources/soliditycode/contractTrcToken023.sol";
    String contractName = "tokenTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String transferTokenTxid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit, 0L, 0,
            10000, assetAccountId.toStringUtf8(), 200, null, dev001Key, dev001Address,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(transferTokenTxid, blockingStubFull);
    logger.info("Delpoy ucrtotal is " + infoById.get().getReceipt().getUcrUsageTotal());

    if (transferTokenTxid == null || infoById.get().getResultValue() != 0) {
      Assert.fail("deploy transaction failed with message: " + infoById.get().getResMessage());
    }

    transferTokenContractAddress = infoById.get().getContractAddress().toByteArray();
    SmartContract smartContract = PublicMethed
        .getContract(transferTokenContractAddress, blockingStubFull);
    Assert.assertNotNull(smartContract.getAbi());

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    ucrLimit = accountResource.getUcrLimit();
    ucrUsage = accountResource.getUcrUsed();
    long balanceAfter = PublicMethed.queryAccount(dev001Key, blockingStubFull).getBalance();
    Long devAssetCountAfter = PublicMethed
        .getAssetIssueValue(dev001Address, assetAccountId, blockingStubFull);

    logger.info("after ucrLimit is " + ucrLimit);
    logger.info("after ucrUsage is " + ucrUsage);
    logger.info("after balanceAfter is " + balanceAfter);
    logger.info("after AssetId: " + assetAccountId.toStringUtf8() + ", devAssetCountAfter: "
        + devAssetCountAfter);

    /*Assert.assertFalse(PublicMethed
        .transferAsset(transferTokenContractAddress, assetAccountId.toByteArray(), 100L,
            dev001Address, dev001Key, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);*/
    Long contractAssetCount = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId, blockingStubFull);
    logger.info("Contract has AssetId: " + assetAccountId.toStringUtf8() + ", Count: "
        + contractAssetCount);

    Assert.assertEquals(Long.valueOf(200), Long.valueOf(devAssetCountBefore - devAssetCountAfter));
    Assert.assertEquals(Long.valueOf(200), contractAssetCount);

    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(fromAddress,
        PublicMethed.getCdBalanceCount(user001Address, user001Key, 50000L, blockingStubFull), 0,
        1, ByteString.copyFrom(user001Address), testKey002, blockingStubFull));

    Assert.assertTrue(PublicMethed
        .transferAsset(user001Address, assetAccountId.toByteArray(), 10L, dev001Address, dev001Key,
            blockingStubFull));

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devUcrLimitBefore = accountResource.getUcrLimit();
    long devUcrUsageBefore = accountResource.getUcrUsed();
    long devBalanceBefore = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("before trigger, devUcrLimitBefore is " + devUcrLimitBefore);
    logger.info("before trigger, devUcrUsageBefore is " + devUcrUsageBefore);
    logger.info("before trigger, devBalanceBefore is " + devBalanceBefore);

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitBefore = accountResource.getUcrLimit();
    long userUcrUsageBefore = accountResource.getUcrUsed();
    long userBalanceBefore = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("before trigger, userUcrLimitBefore is " + userUcrLimitBefore);
    logger.info("before trigger, userUcrUsageBefore is " + userUcrUsageBefore);
    logger.info("before trigger, userBalanceBefore is " + userBalanceBefore);

    Long transferAssetBefore = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId, blockingStubFull);
    logger.info(
        "before trigger, transferTokenContractAddress has AssetId " + assetAccountId.toStringUtf8()
            + ", Count is " + transferAssetBefore);

    Long receiveAssetBefore = PublicMethed
        .getAssetIssueValue(tmpAddress, assetAccountId, blockingStubFull);
    logger.info(
        "before trigger, receiveTokenContractAddress has AssetId " + assetAccountId.toStringUtf8()
            + ", Count is " + receiveAssetBefore);

    String param =
        "\"" + Base58.encode58Check(tmpAddress) + "\"," + assetAccountId.toStringUtf8() + ",\"1\"";

    final String triggerTxid = PublicMethed
        .triggerContract(transferTokenContractAddress, "TransferTokenTo(address,trcToken,uint256)",
            param, false, 0, 1000000000L, assetAccountId.toStringUtf8(), 2, user001Address,
            user001Key, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethed.getAccountResource(dev001Address, blockingStubFull);
    long devUcrLimitAfter = accountResource.getUcrLimit();
    long devUcrUsageAfter = accountResource.getUcrUsed();
    long devBalanceAfter = PublicMethed.queryAccount(dev001Address, blockingStubFull).getBalance();

    logger.info("after trigger, devUcrLimitAfter is " + devUcrLimitAfter);
    logger.info("after trigger, devUcrUsageAfter is " + devUcrUsageAfter);
    logger.info("after trigger, devBalanceAfter is " + devBalanceAfter);

    accountResource = PublicMethed.getAccountResource(user001Address, blockingStubFull);
    long userUcrLimitAfter = accountResource.getUcrLimit();
    long userUcrUsageAfter = accountResource.getUcrUsed();
    long userBalanceAfter = PublicMethed.queryAccount(user001Address, blockingStubFull)
        .getBalance();

    logger.info("after trigger, userUcrLimitAfter is " + userUcrLimitAfter);
    logger.info("after trigger, userUcrUsageAfter is " + userUcrUsageAfter);
    logger.info("after trigger, userBalanceAfter is " + userBalanceAfter);

    infoById = PublicMethed.getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertEquals(SUCESS, infoById.get().getResult());
    logger.info("Trigger ucrtotal is " + infoById.get().getReceipt().getUcrUsageTotal());
    logger.info("infoById.get().getResMessage().toStringUtf8(): " + infoById.get().getResMessage()
        .toStringUtf8());
    /*Assert.assertEquals(
        "transfer src10 failed: Validate InternalTransfer error, no ToAccount. "
            + "And not allowed to create account in smart contract.",
        infoById.get().getResMessage().toStringUtf8());*/

    Long transferAssetAfter = PublicMethed
        .getAssetIssueValue(transferTokenContractAddress, assetAccountId, blockingStubFull);
    logger.info(
        "after trigger, transferTokenContractAddress has AssetId " + assetAccountId.toStringUtf8()
            + ", transferAssetAfter is " + transferAssetAfter);

    Long receiveAssetAfter = PublicMethed
        .getAssetIssueValue(tmpAddress, assetAccountId, blockingStubFull);
    logger.info(
        "after trigger, receiveTokenAddress has AssetId " + assetAccountId.toStringUtf8()
            + ", receiveAssetAfter is " + receiveAssetAfter);

    Assert.assertEquals(receiveAssetAfter, Long.valueOf(receiveAssetBefore + 1));
    Assert.assertEquals(transferAssetBefore, Long.valueOf(transferAssetAfter - 1));

  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(dev001Address, dev001Key, fromAddress, blockingStubFull);
    PublicMethed.freedResource(user001Address, user001Key, fromAddress, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 0, dev001Address, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 0, user001Address, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 1, dev001Address, blockingStubFull);
    PublicMethed.unCdBalance(fromAddress, testKey002, 1, user001Address, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


