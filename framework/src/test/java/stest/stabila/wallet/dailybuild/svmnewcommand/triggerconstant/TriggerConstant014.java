package stest.stabila.wallet.dailybuild.svmnewcommand.triggerconstant;

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
import org.stabila.api.GrpcAPI.TransactionExtention;
import org.stabila.api.WalletGrpc;
import org.stabila.api.WalletSolidityGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class TriggerConstant014 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  byte[] returnAddressBytes = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private ManagedChannel channelSolidity = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private ManagedChannel channelRealSolidity = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubRealSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  private String realSoliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(1);

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
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1).usePlaintext(true).build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode).usePlaintext(true).build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
    channelRealSolidity = ManagedChannelBuilder.forTarget(realSoliditynode).usePlaintext(true)
        .build();
    blockingStubRealSolidity = WalletSolidityGrpc.newBlockingStub(channelRealSolidity);
  }

  @Test(enabled = true, description = "TriggerContract a non-constant function created by create2")
  public void test01TriggerContract() {
    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 1000000000L, testNetAccountAddress, testNetAccountKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "src/test/resources/soliditycode/ClearAbi005.sol";
    String contractName = "Factory";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Account info;

    AccountResourceMessage resourceInfo = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    info = PublicMethed.queryAccount(contractExcKey, blockingStubFull);
    Long beforeBalance = info.getBalance();
    Long beforeUcrUsed = resourceInfo.getUcrUsed();
    Long beforeNetUsed = resourceInfo.getNetUsed();
    Long beforeFreeNetUsed = resourceInfo.getFreeNetUsed();
    logger.info("beforeBalance:" + beforeBalance);
    logger.info("beforeUcrUsed:" + beforeUcrUsed);
    logger.info("beforeNetUsed:" + beforeNetUsed);
    logger.info("beforeFreeNetUsed:" + beforeFreeNetUsed);

    String contractName1 = "TestConstract";
    HashMap retMap1 = PublicMethed.getBycodeAbi(filePath, contractName1);
    String code1 = retMap1.get("byteCode").toString();
    String abi1 = retMap1.get("abI").toString();
    String txid = "";
    String num = "\"" + code1 + "\"" + "," + 1;
    txid = PublicMethed
        .triggerContract(contractAddress, "deploy(bytes,uint256)", num, false, 0, maxFeeLimit, "0",
            0, contractExcAddress, contractExcKey, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
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
    AccountResourceMessage resourceInfoafter = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    Long afterBalance = infoafter.getBalance();
    Long afterUcrUsed = resourceInfoafter.getUcrUsed();
    Long afterNetUsed = resourceInfoafter.getNetUsed();
    Long afterFreeNetUsed = resourceInfoafter.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance);
    logger.info("afterUcrUsed:" + afterUcrUsed);
    logger.info("afterNetUsed:" + afterNetUsed);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed);

    Assert.assertTrue(infoById.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance + fee == beforeBalance);
    Assert.assertTrue(beforeUcrUsed + ucrUsed >= afterUcrUsed);
    Assert.assertTrue(beforeFreeNetUsed + netUsed >= afterFreeNetUsed);
    Assert.assertTrue(beforeNetUsed + netUsed >= afterNetUsed);
    returnAddressBytes = infoById.get().getInternalTransactions(0).getTransferToAddress()
        .toByteArray();
    String returnAddress = Base58.encode58Check(returnAddressBytes);
    logger.info("returnAddress:" + returnAddress);
    txid = PublicMethed
        .triggerContract(returnAddressBytes, "plusOne()", "#", false, 0, maxFeeLimit, "0", 0,
            contractExcAddress, contractExcKey, blockingStubFull);
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
    AccountResourceMessage resourceInfoafter1 = PublicMethed
        .getAccountResource(contractExcAddress, blockingStubFull);
    Long afterBalance1 = infoafter1.getBalance();
    Long afterUcrUsed1 = resourceInfoafter1.getUcrUsed();
    Long afterNetUsed1 = resourceInfoafter1.getNetUsed();
    Long afterFreeNetUsed1 = resourceInfoafter1.getFreeNetUsed();
    logger.info("afterBalance:" + afterBalance1);
    logger.info("afterUcrUsed:" + afterUcrUsed1);
    logger.info("afterNetUsed:" + afterNetUsed1);
    logger.info("afterFreeNetUsed:" + afterFreeNetUsed1);

    Assert.assertTrue(infoById1.get().getResultValue() == 0);
    Assert.assertTrue(afterBalance1 + fee1 == afterBalance);
    Assert.assertTrue(afterUcrUsed + ucrUsed1 >= afterUcrUsed1);
    Long returnnumber = ByteArray.toLong(ByteArray
        .fromHexString(ByteArray.toHexString(infoById1.get().getContractResult(0).toByteArray())));
    Assert.assertTrue(1 == returnnumber);
  }

  @Test(enabled = true, description = "TriggerConstantContract a non-constant function "
      + "created by create2")
  public void test16TriggerConstantContract() {

    String returnAddress = Base58.encode58Check(returnAddressBytes);
    logger.info("returnAddress:" + returnAddress);
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(returnAddressBytes, "plusOne()", "#", false, 0,
            maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    System.out.println("Code = " + transactionExtention.getResult().getCode());
    System.out.println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());

    Assert
        .assertThat(transactionExtention.getResult().getCode().toString(),
            containsString("SUCCESS"));
    /*Assert
        .assertThat(transactionExtention.getResult().getMessage().toStringUtf8(),
            containsString("Attempt to call a state modifying opcode inside STATICCALL"));*/
  }

  @Test(enabled = true, description = "TriggerConstantContract a non-constant function "
      + "created by create2 on solidity")
  public void test16TriggerConstantContractOnSolidity() {
    String returnAddress = Base58.encode58Check(returnAddressBytes);
    logger.info("returnAddress:" + returnAddress);
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtentionOnSolidity(returnAddressBytes, "plusOne()", "#", false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubSolidity);
    System.out.println("Code = " + transactionExtention.getResult().getCode());
    System.out.println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());

    Assert
        .assertThat(transactionExtention.getResult().getCode().toString(),
            containsString("SUCCESS"));
    /*Assert
        .assertThat(transactionExtention.getResult().getMessage().toStringUtf8(),
            containsString("Attempt to call a state modifying opcode inside STATICCALL"));*/
  }

  @Test(enabled = true, description = "TriggerConstantContract a non-constant function "
      + "created by create2 on real solidity")
  public void test16TriggerConstantContractOnRealSolidity() {
    String returnAddress = Base58.encode58Check(returnAddressBytes);
    logger.info("returnAddress:" + returnAddress);
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtentionOnSolidity(returnAddressBytes, "plusOne()", "#", false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubRealSolidity);
    System.out.println("Code = " + transactionExtention.getResult().getCode());
    System.out.println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());

    Assert
        .assertThat(transactionExtention.getResult().getCode().toString(),
            containsString("SUCCESS"));
    /*Assert
        .assertThat(transactionExtention.getResult().getMessage().toStringUtf8(),
            containsString("Attempt to call a state modifying opcode inside STATICCALL"));*/
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
