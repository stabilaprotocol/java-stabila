package stest.stabila.wallet.fulltest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class SvmContract {

  //testng001、testng002、testng003、testng004
  private final String testKey002 =
      "FC8BF0238748587B9617EB6D15D47A66C0E07C1A1959033CF249C6532DC29FE6";
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract008Address = ecKey1.getAddress();
  String contract008Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
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

  @BeforeClass(enabled = false)
  public void beforeClass() {
    PublicMethed.printAddress(contract008Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    Assert.assertTrue(PublicMethed.sendcoin(contract008Address, 500000000L, fromAddress,
        testKey002, blockingStubFull));
    logger.info(Long.toString(PublicMethed.queryAccount(contract008Key, blockingStubFull)
        .getBalance()));
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(contract008Address, 1000000L,
        3, 1, contract008Key, blockingStubFull));
    Assert.assertTrue(PublicMethed.buyStorage(50000000L, contract008Address, contract008Key,
        blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalance(contract008Address, 5000000L,
        3, contract008Key, blockingStubFull));

  }

  @Test(enabled = false)
  public void deployErc721CryptoKitties() {
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract008Address,
        blockingStubFull);
    Long ucrLimit = accountResource.getUcrLimit();
    Long storageLimit = accountResource.getStorageLimit();
    Long ucrUsage = accountResource.getUcrUsed();
    Long storageUsage = accountResource.getStorageUsed();

    logger.info("before ucr limit is " + Long.toString(ucrLimit));
    logger.info("before ucr usage is " + Long.toString(ucrUsage));
    logger.info("before storage limit is " + Long.toString(storageLimit));
    logger.info("before storage usaged is " + Long.toString(storageUsage));
    Long maxFeeLimit = 50000000L;
    String contractName = "ERC721";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_SvmContract_deployErc721CryptoKitties");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_SvmContract_deployErc721CryptoKitties");
    Long m = 0L;
    Long freeNet;
    accountResource = PublicMethed.getAccountResource(contract008Address, blockingStubFull);
    Long net = accountResource.getFreeNetUsed();
    Account account = PublicMethed.queryAccount(contract008Key, blockingStubFull);
    Long netUsed = account.getNetUsage();
    logger.info("before net used is " + Long.toString(netUsed));
    logger.info("before balance is " + account.getBalance());

    for (Integer i = 0; i < 1; i++) {
      byte[] contractAddress = PublicMethed.deployContract("1", abi, code, "",
          30000000L, 0L, 1, null, contract008Key, contract008Address, blockingStubFull);
      accountResource = PublicMethed.getAccountResource(contract008Address, blockingStubFull);
      freeNet = accountResource.getFreeNetUsed();
      ucrUsage = accountResource.getUcrUsed();
      logger.info(
          "Time " + Integer.toString(i) + ": ucr usage is " + Long.toString(ucrUsage - m));
      logger.info("Time " + Integer.toString(i) + ": free net used is " + Long
          .toString(freeNet - net));
      account = PublicMethed.queryAccount(contract008Key, blockingStubFull);
      logger.info("after balance is " + account.getBalance());
      netUsed = account.getNetUsage();
      logger.info("after net used is " + Long.toString(netUsed));
      net = freeNet;
      m = ucrUsage;
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }
    //SmartContract smartContract = PublicMethed.getContract(contractAddress,blockingStubFull);

    //Assert.assertFalse(smartContract.getAbi().toString().isEmpty());
    //Assert.assertTrue(smartContract.getName().equalsIgnoreCase(contractName));
    //Assert.assertFalse(smartContract.getBytecode().toString().isEmpty());
    //logger.info(smartContract.getName());
    //logger.info(smartContract.getAbi().toString());
    accountResource = PublicMethed.getAccountResource(contract008Address, blockingStubFull);
    ucrLimit = accountResource.getUcrLimit();
    storageLimit = accountResource.getStorageLimit();
    ucrUsage = accountResource.getUcrUsed();
    storageUsage = accountResource.getStorageUsed();
    //Assert.assertTrue(storageUsage > 0);
    //Assert.assertTrue(storageLimit > 0);
    //Assert.assertTrue(ucrLimit > 0);
    //Assert.assertTrue(ucrUsage > 0);

    logger.info("after ucr limit is " + Long.toString(ucrLimit));
    logger.info("after ucr usage is " + Long.toString(ucrUsage));
    logger.info("after storage limit is " + Long.toString(storageLimit));
    logger.info("after storage usaged is " + Long.toString(storageUsage));
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


