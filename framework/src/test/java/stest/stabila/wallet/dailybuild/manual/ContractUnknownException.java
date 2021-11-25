package stest.stabila.wallet.dailybuild.manual;

import static org.hamcrest.core.StringContains.containsString;

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
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractUnknownException {


  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] grammarAddress = ecKey1.getAddress();
  String testKeyForGrammarAddress = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] grammarAddress2 = ecKey2.getAddress();
  String testKeyForGrammarAddress2 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  ECKey ecKey3 = new ECKey(Utils.getRandom());
  byte[] grammarAddress3 = ecKey3.getAddress();
  String testKeyForGrammarAddress3 = ByteArray.toHexString(ecKey3.getPrivKeyBytes());
  ECKey ecKey4 = new ECKey(Utils.getRandom());
  byte[] grammarAddress4 = ecKey4.getAddress();
  String testKeyForGrammarAddress4 = ByteArray.toHexString(ecKey4.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelSolidity = null;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);

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
    PublicMethed.printAddress(testKeyForGrammarAddress);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    logger.info(Long.toString(PublicMethed.queryAccount(testNetAccountKey, blockingStubFull)
        .getBalance()));
  }

  @Test(enabled = true, description = "trigger selfdestruct method")
  public void testGrammar001() {
    Assert.assertTrue(PublicMethed
        .sendcoin(grammarAddress, 1000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(grammarAddress, 204800000,
        0, 1, testKeyForGrammarAddress, blockingStubFull));
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(grammarAddress,
        blockingStubFull);
    info = PublicMethed.queryAccount(grammarAddress, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    long beforeucrLimit = resourceInfo.getUcrLimit();

    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("beforeucrLimit:" + beforeucrLimit);
    String filePath = "src/test/resources/soliditycode/contractUnknownException.sol";
    String contractName = "testA";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            20L, 100, null, testKeyForGrammarAddress,
            grammarAddress, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    final String s = infoById.get().getResMessage().toStringUtf8();
    long fee = infoById.get().getFee();
    long ucrUsage = infoById.get().getReceipt().getUcrUsage();
    long ucrFee = infoById.get().getReceipt().getUcrFee();
    Account infoafter = PublicMethed.queryAccount(grammarAddress, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(grammarAddress,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfo.getNetUsed();
    Long afterFreeNetUsed = resourceInfo.getFreeNetUsed();
    long aftereucrLimit = resourceInfo.getUcrLimit();

    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("afterucrLimit:" + aftereucrLimit);
    Assert.assertThat(s, containsString("REVERT opcode executed"));
    PublicMethed.unCdBalance(grammarAddress, testKeyForGrammarAddress, 1, grammarAddress,
        blockingStubFull);
    PublicMethed.freedResource(grammarAddress, testKeyForGrammarAddress, testNetAccountAddress,
        blockingStubFull);
  }

  @Test(enabled = true, description = "trigger revert method")
  public void testGrammar002() {
    Assert.assertTrue(PublicMethed
        .sendcoin(grammarAddress2, 100000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(grammarAddress2, 10000000L,
        0, 1, testKeyForGrammarAddress2, blockingStubFull));
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(grammarAddress2,
        blockingStubFull);
    info = PublicMethed.queryAccount(grammarAddress2, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    long beforeucrLimit = resourceInfo.getUcrLimit();

    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("beforeucrLimit:" + beforeucrLimit);
    String filePath = "src/test/resources/soliditycode/contractUnknownException.sol";
    String contractName = "testB";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            20L, 100, null, testKeyForGrammarAddress2,
            grammarAddress2, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    final long fee = infoById.get().getFee();
    final long ucrUsage = infoById.get().getReceipt().getUcrUsage();
    final long ucrFee = infoById.get().getReceipt().getUcrFee();

    final String s = infoById.get().getResMessage().toStringUtf8();

    Account infoafter = PublicMethed.queryAccount(grammarAddress2, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(grammarAddress2,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfo.getNetUsed();
    Long afterFreeNetUsed = resourceInfo.getFreeNetUsed();
    long aftereucrLimit = resourceInfo.getUcrLimit();

    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("afterucrLimit:" + aftereucrLimit);
    Assert.assertThat(s, containsString("REVERT opcode executed"));
    Assert.assertFalse(ucrFee == 1000000000);

    Assert.assertTrue(beforeBalance - fee == afterBalance);
    PublicMethed.unCdBalance(grammarAddress2, testKeyForGrammarAddress2, 1, grammarAddress2,
        blockingStubFull);
    PublicMethed.freedResource(grammarAddress2, testKeyForGrammarAddress2, testNetAccountAddress,
        blockingStubFull);

  }

  @Test(enabled = true, description = "trigger assert method")
  public void testGrammar003() {
    Assert.assertTrue(PublicMethed
        .sendcoin(grammarAddress3, 100000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(grammarAddress3, 1000000000L,
        0, 1, testKeyForGrammarAddress3, blockingStubFull));
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(grammarAddress3,
        blockingStubFull);
    info = PublicMethed.queryAccount(grammarAddress3, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    long beforeucrLimit = resourceInfo.getUcrLimit();

    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("beforeucrLimit:" + beforeucrLimit);
    String filePath = "src/test/resources/soliditycode/contractUnknownException.sol";
    String contractName = "testC";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            20L, 100, null, testKeyForGrammarAddress3,
            grammarAddress3, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    final long fee = infoById.get().getFee();
    final long ucrUsage = infoById.get().getReceipt().getUcrUsage();
    final long ucrFee = infoById.get().getReceipt().getUcrFee();
    String s = infoById.get().getResMessage().toStringUtf8();
    Account infoafter = PublicMethed.queryAccount(grammarAddress3, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(grammarAddress3,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfo.getNetUsed();
    Long afterFreeNetUsed = resourceInfo.getFreeNetUsed();
    long aftereucrLimit = resourceInfo.getUcrLimit();

    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("afterucrLimit:" + aftereucrLimit);
    logger.info("s:" + s);
    Assert.assertThat(s, containsString("REVERT opcode executed"));
    Assert.assertTrue(beforeBalance - fee == afterBalance);
    PublicMethed.unCdBalance(grammarAddress3, testKeyForGrammarAddress3, 1, grammarAddress3,
        blockingStubFull);
    PublicMethed.freedResource(grammarAddress3, testKeyForGrammarAddress3, testNetAccountAddress,
        blockingStubFull);

  }


  @Test(enabled = true, description = "trigger require method")
  public void testGrammar004() {
    Assert.assertTrue(PublicMethed
        .sendcoin(grammarAddress4, 100000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(grammarAddress4, 100000000L,
        0, 1, testKeyForGrammarAddress4, blockingStubFull));
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(grammarAddress4,
        blockingStubFull);
    info = PublicMethed.queryAccount(grammarAddress4, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    long beforeucrLimit = resourceInfo.getUcrLimit();

    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);
    logger.info("beforeucrLimit:" + beforeucrLimit);
    String filePath = "src/test/resources/soliditycode/contractUnknownException.sol";
    String contractName = "testD";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            20L, 100, null, testKeyForGrammarAddress4,
            grammarAddress4, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    final String s = infoById.get().getResMessage().toStringUtf8();
    final long fee = infoById.get().getFee();
    long ucrUsage = infoById.get().getReceipt().getUcrUsage();
    final long ucrFee = infoById.get().getReceipt().getUcrFee();

    Account infoafter = PublicMethed.queryAccount(grammarAddress4, blockingStubFull);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(grammarAddress4,
        blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfo.getNetUsed();
    Long afterFreeNetUsed = resourceInfo.getFreeNetUsed();
    long aftereucrLimit = resourceInfo.getUcrLimit();

    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);
    logger.info("afterucrLimit:" + aftereucrLimit);
    Assert.assertThat(s, containsString("REVERT opcode executed"));
    Assert.assertTrue(beforeBalance - fee == afterBalance);
    Assert.assertFalse(ucrFee == 1000000000);
    PublicMethed.unCdBalance(grammarAddress4, testKeyForGrammarAddress4, 1, grammarAddress4,
        blockingStubFull);
    PublicMethed.freedResource(grammarAddress4, testKeyForGrammarAddress4, testNetAccountAddress,
        blockingStubFull);
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

}
