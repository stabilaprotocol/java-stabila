package stest.stabila.wallet.contract.linkage;

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
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractLinkage003 {

  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey003);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] linkage003Address = ecKey1.getAddress();
  String linkage002Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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
    PublicMethed.printAddress(linkage002Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true)
  public void deployWhenNoUcr() {
    Assert.assertTrue(PublicMethed.sendcoin(linkage003Address, 200000000L, fromAddress,
        testKey003, blockingStubFull));
    Account info;
    AccountResourceMessage resourceInfo = PublicMethed.getAccountResource(linkage003Address,
        blockingStubFull);
    info = PublicMethed.queryAccount(linkage003Address, blockingStubFull);
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

    String filePath = "src/test/resources/soliditycode//contractLinkage003.sol";
    String contractName = "divideIHaveArgsReturnStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    //use FreeNet and balance,UcrUsed==0.
    String txid = PublicMethed
        .deployContractAndGetTransactionInfoById(contractName, abi, code, "", maxFeeLimit,
            0L, 0, null, linkage002Key, linkage003Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = null;
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    byte[] contractAddress = infoById.get().getContractAddress().toByteArray();
    Long ucrUsageTotal = infoById.get().getReceipt().getUcrUsageTotal();
    Long fee = infoById.get().getFee();
    Long ucrFee = infoById.get().getReceipt().getUcrFee();
    Long netUsed = infoById.get().getReceipt().getNetUsage();
    Long ucrUsed = infoById.get().getReceipt().getUcrUsage();
    Long netFee = infoById.get().getReceipt().getNetFee();
    logger.info("ucrUsageTotal:" + ucrUsageTotal);
    logger.info("fee:" + fee);
    logger.info("ucrFee:" + ucrFee);
    logger.info("netUsed:" + netUsed);
    logger.info("ucrUsed:" + ucrUsed);
    logger.info("netFee:" + netFee);

    Account infoafter = PublicMethed.queryAccount(linkage003Address, blockingStubFull1);
    AccountResourceMessage resourceInfoafter = PublicMethed.getAccountResource(linkage003Address,
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

    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertFalse(smartContract.getName().isEmpty());
    Assert.assertTrue((beforeBalance - fee) == afterBalance);
    Assert.assertTrue(afterUcrUsed == 0L);
    Assert.assertTrue(afterFreeNetUsed > 0L);
    Assert.assertTrue(afterNetUsed == 0L);


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


