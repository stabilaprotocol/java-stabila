package stest.stabila.wallet.dailybuild.delaytransaction;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class DelayTransaction005 {

  private static final long now = System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  Long delaySecond = 10L;
  ByteString assetId;
  SmartContract smartContract;
  ECKey ecKey = new ECKey(Utils.getRandom());
  byte[] smartContractOwnerAddress = ecKey.getAddress();
  String smartContractOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
  private Long delayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.delayTransactionFee");
  private Long cancleDelayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.cancleDelayTransactionFee");
  private byte[] contractAddress = null;

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
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = false, description = "Delay update ucr limit contract")
  public void test1DelayUpdateSetting() {
    //get account
    ecKey = new ECKey(Utils.getRandom());
    smartContractOwnerAddress = ecKey.getAddress();
    smartContractOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    PublicMethed.printAddress(smartContractOwnerKey);

    Assert.assertTrue(PublicMethed.sendcoin(smartContractOwnerAddress, 2048000000, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.cdBalance(smartContractOwnerAddress, 10000000L, 3,
        smartContractOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String contractName = "STABILATOKEN";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_ContractScenario004_deployErc20StabilaToken");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_ContractScenario004_deployErc20StabilaToken");
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, 999L, "0", 0, null,
        smartContractOwnerKey, smartContractOwnerAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Long oldOriginUcrLimit = 567L;
    Assert.assertTrue(PublicMethed.updateUcrLimit(contractAddress, oldOriginUcrLimit,
        smartContractOwnerKey, smartContractOwnerAddress, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getOriginUcrLimit() == oldOriginUcrLimit);

    Long newOriginUcrLimit = 8765L;
    final String txid = PublicMethed.updateUcrLimitDelayGetTxid(contractAddress,
        newOriginUcrLimit, delaySecond, smartContractOwnerKey, smartContractOwnerAddress,
        blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getOriginUcrLimit() == oldOriginUcrLimit);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    logger.info("newOriginUcrLimit: " + smartContract.getOriginUcrLimit());
    Assert.assertTrue(smartContract.getOriginUcrLimit() == newOriginUcrLimit);

    Long netFee = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get().getReceipt()
        .getNetFee();
    Long fee = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get().getFee();
    Assert.assertTrue(fee - netFee == delayTransactionFee);

  }

  @Test(enabled = false, description = "Cancel delay ucr limit contract")
  public void test2CancelDelayUpdateSetting() {
    //get account
    final Long oldOriginUcrLimit = smartContract.getOriginUcrLimit();
    final Long newOriginUcrLimit = 466L;

    String txid = PublicMethed.updateUcrLimitDelayGetTxid(contractAddress, newOriginUcrLimit,
        delaySecond, smartContractOwnerKey, smartContractOwnerAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account ownerAccount = PublicMethed.queryAccount(smartContractOwnerKey, blockingStubFull);
    final Long beforeCancelBalance = ownerAccount.getBalance();

    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid, fromAddress, testKey002,
        blockingStubFull));
    final String cancelTxid = PublicMethed.cancelDeferredTransactionByIdGetTxid(txid,
        smartContractOwnerAddress, smartContractOwnerKey, blockingStubFull);
    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid, smartContractOwnerAddress,
        smartContractOwnerKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    logger.info("newOriginUcrLimit: " + smartContract.getOriginUcrLimit());
    Assert.assertTrue(smartContract.getOriginUcrLimit() == oldOriginUcrLimit);

    final Long netFee = PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull).get()
        .getReceipt().getNetFee();
    final Long fee = PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull).get()
        .getFee();
    logger.info("net fee : " + PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull)
        .get().getReceipt().getNetFee());
    logger.info("Fee : " + PublicMethed.getTransactionInfoById(cancelTxid, blockingStubFull)
        .get().getFee());

    ownerAccount = PublicMethed.queryAccount(smartContractOwnerKey, blockingStubFull);
    Long afterCancelBalance = ownerAccount.getBalance();
    Assert.assertTrue(fee - netFee == cancleDelayTransactionFee);
    Assert.assertTrue(fee == beforeCancelBalance - afterCancelBalance);

    logger.info("beforeCancelBalance: " + beforeCancelBalance);
    logger.info("afterCancelBalance : " + afterCancelBalance);
  }

  /**
   * constructor.
   */

  @AfterClass(enabled = false)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


