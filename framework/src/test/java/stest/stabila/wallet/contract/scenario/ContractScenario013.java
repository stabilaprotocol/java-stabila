package stest.stabila.wallet.contract.scenario;

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
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractScenario013 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  byte[] contractAddress = null;
  String txid = "";
  Optional<TransactionInfo> infoById = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract013Address = ecKey1.getAddress();
  String contract013Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
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
    PublicMethed.printAddress(contract013Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true)
  public void deployStabilaStbAndUnitContract() {
    Assert.assertTrue(PublicMethed.sendcoin(contract013Address, 20000000000L, fromAddress,
        testKey002, blockingStubFull));
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract013Address,
        blockingStubFull);
    Long ucrLimit = accountResource.getUcrLimit();
    Long ucrUsage = accountResource.getUcrUsed();

    logger.info("before ucr limit is " + Long.toString(ucrLimit));
    logger.info("before ucr usage is " + Long.toString(ucrUsage));

    String filePath = "./src/test/resources/soliditycode/contractScenario013.sol";
    String contractName = "timetest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code, "",
        maxFeeLimit, 0L, 100, null, contract013Key, contract013Address, blockingStubFull);
    logger.info(txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("ucrtotal is " + infoById.get().getReceipt().getUcrUsageTotal());
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 0);
    Assert.assertFalse(infoById.get().getContractAddress().isEmpty());
  }

  @Test(enabled = true)
  public void triggerStabilaStbAndUnitContract() {
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract013Address,
        blockingStubFull);
    Long ucrLimit = accountResource.getUcrLimit();
    Long ucrUsage = accountResource.getUcrUsed();

    logger.info("before ucr limit is " + Long.toString(ucrLimit));
    logger.info("before ucr usage is " + Long.toString(ucrUsage));

    String filePath = "./src/test/resources/soliditycode/contractScenario013.sol";
    String contractName = "timetest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            0L, 100, null, contract013Key, contract013Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    logger.info("ucrtotal is " + infoById.get().getReceipt().getUcrUsageTotal());

    contractAddress = infoById.get().getContractAddress().toByteArray();

    txid = PublicMethed.triggerContract(contractAddress,
        "time()", "#", false,
        0, 100000000L, contract013Address, contract013Key, blockingStubFull);
    logger.info(txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("result is " + infoById.get().getResultValue());
    logger.info("ucrtotal is " + infoById.get().getReceipt().getUcrUsageTotal());
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 0);
    Assert.assertTrue(infoById.get().getFee() == infoById.get().getReceipt().getUcrFee());
    Assert.assertFalse(infoById.get().getContractAddress().isEmpty());
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


