package stest.stabila.wallet.contract.scenario;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
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
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractScenario009 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract009Address = ecKey1.getAddress();
  String contract009Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String compilerVersion = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.solidityCompilerVersion");

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
    PublicMethed.printAddress(contract009Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true)
  public void deployContainLibraryContract() {
    Assert.assertTrue(PublicMethed.sendcoin(contract009Address, 20000000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(contract009Address, 1000000L,
        3, 1, contract009Key, blockingStubFull));
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract009Address,
        blockingStubFull);
    Long ucrLimit = accountResource.getUcrLimit();
    Long ucrUsage = accountResource.getUcrUsed();

    logger.info("before ucr limit is " + Long.toString(ucrLimit));
    logger.info("before ucr usage is " + Long.toString(ucrUsage));
    String filePath = "./src/test/resources/soliditycode/contractScenario009.sol";
    String contractName = "Set";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    byte[] libraryContractAddress;
    libraryContractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit,
            0L, 100, null, contract009Key, contract009Address, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    contractName = "C";
    retMap = PublicMethed.getBycodeAbiForLibrary(filePath, contractName);
    code = retMap.get("byteCode").toString();
    abi = retMap.get("abI").toString();
    String library = retMap.get("library").toString();

    //String libraryAddress =
    //    "browser/SvmTest_p1_Grammar_002.sol:Set:" + Base58.encode58Check(libraryContractAddress);
    String libraryAddress;
    libraryAddress = library
        + Base58.encode58Check(libraryContractAddress);

    byte[] contractAddress = PublicMethed
        .deployContractForLibrary(contractName, abi, code, "", maxFeeLimit, 0L, 100, libraryAddress,
            contract009Key, contract009Address, compilerVersion, blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);

    Assert.assertFalse(smartContract.getAbi().toString().isEmpty());
    Assert.assertTrue(smartContract.getName().equalsIgnoreCase(contractName));
    Assert.assertFalse(smartContract.getBytecode().toString().isEmpty());
    logger.info(ByteArray.toHexString(smartContract.getContractAddress().toByteArray()));
    accountResource = PublicMethed.getAccountResource(contract009Address, blockingStubFull);
    ucrLimit = accountResource.getUcrLimit();
    ucrUsage = accountResource.getUcrUsed();
    Assert.assertTrue(ucrLimit > 0);
    Assert.assertTrue(ucrUsage > 0);

    logger.info("after ucr limit is " + Long.toString(ucrLimit));
    logger.info("after ucr usage is " + Long.toString(ucrUsage));
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


