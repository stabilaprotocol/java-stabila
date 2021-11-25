package stest.stabila.wallet.dailybuild.manual;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.springframework.util.StringUtils;
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
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractScenario011 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  String kittyCoreAddressAndCut = "";
  byte[] kittyCoreContractAddress = null;
  byte[] saleClockAuctionContractAddress = null;
  byte[] siringClockAuctionContractAddress = null;
  byte[] geneScienceInterfaceContractAddress = null;
  Integer consumeUserResourcePercent = 50;
  String txid = "";
  Optional<TransactionInfo> infoById = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] deployAddress = ecKey1.getAddress();
  String deployKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] triggerAddress = ecKey2.getAddress();
  String triggerKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
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
    PublicMethed.printAddress(deployKey);
    PublicMethed.printAddress(triggerKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    Assert.assertTrue(PublicMethed.sendcoin(deployAddress, 50000000000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed.sendcoin(triggerAddress, 50000000000L, fromAddress,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
  }

  @Test(enabled = true, description = "Deploy Erc721 contract \"Kitty Core\"")
  public void deployErc721KittyCore() {
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(deployAddress, 100000000L,
        0, 1, deployKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Assert.assertTrue(PublicMethed.cdBalance(deployAddress, 100000000L, 0,
        deployKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    Assert.assertTrue(PublicMethed.cdBalance(triggerAddress, 100000000L, 0,
        triggerKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull1);
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(deployAddress,
        blockingStubFull);
    Long cpuLimit = accountResource.getUcrLimit();
    Long cpuUsage = accountResource.getUcrUsed();
    Account account = PublicMethed.queryAccount(deployAddress, blockingStubFull);
    logger.info("before balance is " + Long.toString(account.getBalance()));
    logger.info("before cpu limit is " + Long.toString(cpuLimit));
    logger.info("before cpu usage is " + Long.toString(cpuUsage));
    String contractName = "KittyCore";
    String filePath = "./src/test/resources/soliditycode/contractScenario011.sol";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    logger.info("Kitty Core");
    kittyCoreContractAddress = PublicMethed.deployContract(contractName, abi, code, "",
        maxFeeLimit, 0L, consumeUserResourcePercent, null, deployKey,
        deployAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(kittyCoreContractAddress,
        blockingStubFull);
    Assert.assertFalse(StringUtils.isEmpty(smartContract.getBytecode()));

    Assert.assertTrue(smartContract.getAbi() != null);
    accountResource = PublicMethed.getAccountResource(deployAddress, blockingStubFull);
    cpuLimit = accountResource.getUcrLimit();
    cpuUsage = accountResource.getUcrUsed();
    account = PublicMethed.queryAccount(deployKey, blockingStubFull);
    logger.info("after balance is " + Long.toString(account.getBalance()));
    logger.info("after cpu limit is " + Long.toString(cpuLimit));
    logger.info("after cpu usage is " + Long.toString(cpuUsage));
    logger.info(ByteArray.toHexString(kittyCoreContractAddress));
    logger.info(ByteArray.toHexString(kittyCoreContractAddress).substring(2));

    kittyCoreAddressAndCut = "000000000000000000000000" + ByteArray
        .toHexString(kittyCoreContractAddress).substring(2);
    kittyCoreAddressAndCut = kittyCoreAddressAndCut + "0000000000000000000000000000000000000000000"
        + "000000000000000000100";
  }

  @Test(enabled = true, description = "Deploy Erc721 contract \"Sale Clock Auction\"")
  public void deploySaleClockAuction() {
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(deployAddress,
        blockingStubFull);
    Long cpuLimit = accountResource.getUcrLimit();
    Long cpuUsage = accountResource.getUcrUsed();
    Account account = PublicMethed.queryAccount(deployKey, blockingStubFull);
    logger.info("before balance is " + Long.toString(account.getBalance()));
    logger.info("before cpu limit is " + Long.toString(cpuLimit));
    logger.info("before cpu usage is " + Long.toString(cpuUsage));
    String contractName = "SaleClockAuction";
    String filePath = "./src/test/resources/soliditycode/contractScenario011.sol";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    logger.info("Sale Clock Auction");
    //saleClockAuctionContractAddress;
    String data = "\"" + Base58.encode58Check(kittyCoreContractAddress) + "\"," + 100;
    String deplTxid = PublicMethed
        .deployContractWithConstantParame(contractName, abi, code, "constructor(address,uint256)",
            data, "", maxFeeLimit, 0L, consumeUserResourcePercent, null, deployKey, deployAddress,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info = PublicMethed
        .getTransactionInfoById(deplTxid, blockingStubFull);
    Assert.assertTrue(info.get().getResultValue() == 0);

    saleClockAuctionContractAddress = info.get().getContractAddress().toByteArray();
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(saleClockAuctionContractAddress,
        blockingStubFull);
    Assert.assertFalse(StringUtils.isEmpty(smartContract.getBytecode()));
    Assert.assertTrue(smartContract.getAbi() != null);
    accountResource = PublicMethed.getAccountResource(deployAddress, blockingStubFull);
    cpuLimit = accountResource.getUcrLimit();
    cpuUsage = accountResource.getUcrUsed();
    account = PublicMethed.queryAccount(deployKey, blockingStubFull);
    logger.info("after balance is " + Long.toString(account.getBalance()));
    logger.info("after cpu limit is " + Long.toString(cpuLimit));
    logger.info("after cpu usage is " + Long.toString(cpuUsage));

    String triggerTxid = PublicMethed
        .triggerContract(saleClockAuctionContractAddress, "isSaleClockAuction()", "#", false, 0,
            maxFeeLimit, deployAddress, deployKey, blockingStubFull);
    Optional<TransactionInfo> inFoByid = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    logger.info("Ttttt " + triggerTxid);
    Assert.assertTrue(inFoByid.get().getResultValue() == 0);
  }

  @Test(enabled = true, description = "Deploy Erc721 contract \"Siring Clock Auction\"")
  public void deploySiringClockAuction() {
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(deployAddress,
        blockingStubFull);
    Long cpuLimit = accountResource.getUcrLimit();
    Long cpuUsage = accountResource.getUcrUsed();
    Account account = PublicMethed.queryAccount(deployKey, blockingStubFull);
    logger.info("before balance is " + Long.toString(account.getBalance()));
    logger.info("before cpu limit is " + Long.toString(cpuLimit));
    logger.info("before cpu usage is " + Long.toString(cpuUsage));
    String contractName = "SiringClockAuction";
    String filePath = "./src/test/resources/soliditycode/contractScenario011.sol";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    String data = "\"" + Base58.encode58Check(kittyCoreContractAddress) + "\"," + 100;
    String siringClockAuctionContractAddressTxid = PublicMethed
        .deployContractWithConstantParame(contractName, abi, code, "constructor(address,uint256)",
            data,
            "", maxFeeLimit, 0L, consumeUserResourcePercent, null, deployKey,
            deployAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info2 = PublicMethed
        .getTransactionInfoById(siringClockAuctionContractAddressTxid, blockingStubFull);
    siringClockAuctionContractAddress = info2.get().getContractAddress().toByteArray();
    Assert.assertTrue(info2.get().getResultValue() == 0);
    SmartContract smartContract = PublicMethed.getContract(siringClockAuctionContractAddress,
        blockingStubFull);
    Assert.assertFalse(StringUtils.isEmpty(smartContract.getBytecode()));
    Assert.assertTrue(smartContract.getAbi() != null);
    accountResource = PublicMethed.getAccountResource(deployAddress, blockingStubFull);
    cpuLimit = accountResource.getUcrLimit();
    cpuUsage = accountResource.getUcrUsed();
    account = PublicMethed.queryAccount(deployKey, blockingStubFull);
    logger.info("after balance is " + Long.toString(account.getBalance()));
    logger.info("after cpu limit is " + Long.toString(cpuLimit));
    logger.info("after cpu usage is " + Long.toString(cpuUsage));
  }

  @Test(enabled = true, description = "Deploy Erc721 contract \"Gene Science Interface\"")
  public void deployGeneScienceInterface() {
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(deployAddress,
        blockingStubFull);
    Long cpuLimit = accountResource.getUcrLimit();
    Long cpuUsage = accountResource.getUcrUsed();
    Account account = PublicMethed.queryAccount(deployKey, blockingStubFull);
    logger.info("before balance is " + Long.toString(account.getBalance()));
    logger.info("before cpu limit is " + Long.toString(cpuLimit));
    logger.info("before cpu usage is " + Long.toString(cpuUsage));
    String contractName = "GeneScienceInterface";
    String filePath = "./src/test/resources/soliditycode/contractScenario011.sol";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid = PublicMethed.deployContractAndGetTransactionInfoById(contractName, abi, code,
        "", maxFeeLimit,
        0L, consumeUserResourcePercent, null, deployKey, deployAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info2 = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    geneScienceInterfaceContractAddress = info2.get().getContractAddress().toByteArray();
    Assert.assertTrue(info2.get().getResultValue() == 0);

    SmartContract smartContract = PublicMethed.getContract(geneScienceInterfaceContractAddress,
        blockingStubFull);
    Assert.assertFalse(StringUtils.isEmpty(smartContract.getBytecode()));
    Assert.assertTrue(smartContract.getAbi() != null);
    accountResource = PublicMethed.getAccountResource(deployAddress, blockingStubFull);
    cpuLimit = accountResource.getUcrLimit();
    cpuUsage = accountResource.getUcrUsed();
    account = PublicMethed.queryAccount(deployKey, blockingStubFull);
    logger.info("after balance is " + Long.toString(account.getBalance()));
    logger.info("after cpu limit is " + Long.toString(cpuLimit));
    logger.info("after cpu usage is " + Long.toString(cpuUsage));
  }

  @Test(enabled = true, description = "Set three contract address for Kitty Core, "
      + "set three CXO roles")
  public void triggerToSetThreeContractAddressToKittyCore() {
    //Set SaleAuctionAddress to kitty core.
    String saleContractString = "\"" + Base58.encode58Check(saleClockAuctionContractAddress) + "\"";
    txid = PublicMethed.triggerContract(kittyCoreContractAddress, "setSaleAuctionAddress(address)",
        saleContractString, false, 0, 10000000L, deployAddress, deployKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);

    //Set SiringAuctionAddress to kitty core.
    String siringContractString = "\"" + Base58.encode58Check(siringClockAuctionContractAddress)
        + "\"";
    txid = PublicMethed
        .triggerContract(kittyCoreContractAddress, "setSiringAuctionAddress(address)",
            siringContractString, false, 0, 10000000L, deployAddress, deployKey, blockingStubFull);
    logger.info(txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);

    //Set gen contract to kitty core
    String genContractString = "\"" + Base58.encode58Check(geneScienceInterfaceContractAddress)
        + "\"";
    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "setGeneScienceAddress(address)", genContractString,
        false, 0, 10000000L, deployAddress, deployKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);

    //Start the game.
    Integer result = 1;
    Integer times = 0;
    while (result == 1) {
      txid = PublicMethed.triggerContract(kittyCoreContractAddress, "unpause()", "", false, 0,
          10000000L, deployAddress, deployKey, blockingStubFull);
      PublicMethed.waitProduceNextBlock(blockingStubFull);
      infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
      result = infoById.get().getResultValue();
      if (times++ == 3) {
        break;
      }
    }

    Assert.assertTrue(result == 0);
    logger.info("start the game " + txid);

    //Create one gen0 cat.
    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "createGen0Auction(uint256)", "-1000000000000000", false,
        0, 100000000L, deployAddress, deployKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "gen0CreatedCount()", "#", false,
        0, 100000000L, deployAddress, deployKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "getKitty(uint256)", "1", false, 0, 10000000, triggerAddress,
        triggerKey, blockingStubFull);
    logger.info("getKitty " + txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    String newCxoAddress = "\"" + Base58.encode58Check(triggerAddress)
        + "\"";

    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "setCOO(address)", newCxoAddress, false, 0, 10000000, deployAddress,
        deployKey, blockingStubFull);
    logger.info("COO " + txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "setCFO(address)", newCxoAddress, false, 0, 10000000, deployAddress,
        deployKey, blockingStubFull);
    logger.info("CFO " + txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "setCEO(address)", newCxoAddress, false, 0, 1000000, deployAddress,
        deployKey, blockingStubFull);
    logger.info("CEO " + txid);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
  }

  @Test(enabled = true, description = "Create Gen0 cat")
  public void triggerUseTriggerUcrUsage() {
    ECKey ecKey3 = new ECKey(Utils.getRandom());
    byte[] triggerUseTriggerUcrUsageAddress = ecKey3.getAddress();
    final String triggerUseTriggerUcrUsageKey = ByteArray.toHexString(ecKey3.getPrivKeyBytes());
    Assert.assertTrue(
        PublicMethed.sendcoin(triggerUseTriggerUcrUsageAddress, 100000000000L,
            fromAddress, testKey002, blockingStubFull));
    String newCxoAddress = "\"" + Base58.encode58Check(triggerUseTriggerUcrUsageAddress)
        + "\"";
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    final String txid1;
    final String txid2;
    final String txid3;
    txid1 = PublicMethed.triggerContract(kittyCoreContractAddress,
        "setCOO(address)", newCxoAddress, false, 0, maxFeeLimit, triggerAddress,
        triggerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("COO " + txid);

    txid2 = PublicMethed.triggerContract(kittyCoreContractAddress,
        "setCFO(address)", newCxoAddress, false, 0, maxFeeLimit, triggerAddress,
        triggerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("CFO " + txid);

    txid3 = PublicMethed.triggerContract(kittyCoreContractAddress,
        "setCEO(address)", newCxoAddress, false, 0, maxFeeLimit, triggerAddress,
        triggerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("CEO " + txid);

    infoById = PublicMethed.getTransactionInfoById(txid1, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    infoById = PublicMethed.getTransactionInfoById(txid2, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);
    infoById = PublicMethed.getTransactionInfoById(txid3, blockingStubFull);
    Assert.assertTrue(infoById.get().getResultValue() == 0);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Long beforeBalance = PublicMethed
        .queryAccount(triggerUseTriggerUcrUsageKey, blockingStubFull).getBalance();
    logger.info("before balance is " + Long.toString(beforeBalance));
    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "createGen0Auction(uint256)", "0", false,
        0, 100000000L, triggerUseTriggerUcrUsageAddress, triggerUseTriggerUcrUsageKey,
        blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull1);
    logger.info("Q " + Long
        .toString(infoById.get().getReceipt().getUcrFee()));
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsage() == 0);
    Assert.assertTrue(infoById.get().getReceipt().getUcrFee() > 10000);
    //    Assert.assertTrue(infoById.get().getReceipt().getOriginUcrUsage() > 10000);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal()
        == infoById.get().getReceipt().getUcrFee() / 100 + infoById.get().getReceipt()
        .getOriginUcrUsage());

    Long fee = infoById.get().getFee();
    Long afterBalance = PublicMethed
        .queryAccount(triggerUseTriggerUcrUsageKey, blockingStubFull1).getBalance();
    logger.info("after balance is " + Long.toString(afterBalance));
    logger.info("fee is " + Long.toString(fee));
    Assert.assertTrue(beforeBalance == afterBalance + fee);

    logger.info("before UcrUsage is " + infoById.get().getReceipt().getUcrUsage());
    logger.info("before UcrFee is " + infoById.get().getReceipt().getUcrFee());
    logger.info("before OriginUcrUsage is " + infoById.get().getReceipt()
        .getOriginUcrUsage());
    logger.info("before UcrTotal is " + infoById.get().getReceipt().getUcrUsageTotal());

    Assert.assertTrue(
        PublicMethed.cdBalanceGetUcr(triggerUseTriggerUcrUsageAddress, 100000000L,
            0, 1, triggerUseTriggerUcrUsageKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    beforeBalance = PublicMethed.queryAccount(triggerUseTriggerUcrUsageKey, blockingStubFull)
        .getBalance();
    logger.info("before balance is " + Long.toString(beforeBalance));

    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(triggerUseTriggerUcrUsageAddress, blockingStubFull);
    Long ucrLimit = accountResource.getUcrLimit();
    logger.info("before UcrLimit is " + Long.toString(ucrLimit));

    txid = PublicMethed.triggerContract(kittyCoreContractAddress,
        "createGen0Auction(uint256)", "0", false,
        0, 100000000L, triggerUseTriggerUcrUsageAddress, triggerUseTriggerUcrUsageKey,
        blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull1);
    logger.info("after UcrUsage is " + infoById.get().getReceipt().getUcrUsage());
    logger.info("after UcrFee is " + infoById.get().getReceipt().getUcrFee());
    logger.info("after OriginUcrUsage is " + infoById.get().getReceipt().getOriginUcrUsage());
    logger.info("after UcrTotal is " + infoById.get().getReceipt().getUcrUsageTotal());
    fee = infoById.get().getFee();
    afterBalance = PublicMethed.queryAccount(triggerUseTriggerUcrUsageKey, blockingStubFull1)
        .getBalance();
    logger.info("after balance is " + Long.toString(afterBalance));
    logger.info("fee is " + Long.toString(fee));

    accountResource = PublicMethed
        .getAccountResource(triggerUseTriggerUcrUsageAddress, blockingStubFull1);
    ucrLimit = accountResource.getUcrLimit();

    logger.info("after UcrLimit is " + Long.toString(ucrLimit));

    Assert.assertTrue(infoById.get().getReceipt().getUcrUsage() > 10000);
    Assert.assertTrue(infoById.get().getReceipt().getUcrFee() == 0);

    //Assert.assertTrue(infoById.get().getReceipt().getOriginUcrUsage() > 10000);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() == infoById.get()
        .getReceipt().getUcrUsage() + infoById.get().getReceipt().getOriginUcrUsage());
    //    Assert.assertTrue(infoById.get().getReceipt().getUcrUsage() == infoById.get()
    //        .getReceipt().getOriginUcrUsage());

    Assert.assertTrue(beforeBalance == afterBalance + fee);
    PublicMethed.unCdBalance(deployAddress, deployKey, 1,
        deployAddress, blockingStubFull);
    PublicMethed.unCdBalance(triggerAddress, triggerKey, 1,
        triggerAddress, blockingStubFull);

    PublicMethed
        .unCdBalance(triggerUseTriggerUcrUsageAddress, triggerUseTriggerUcrUsageKey, 1,
            triggerUseTriggerUcrUsageAddress, blockingStubFull);
    PublicMethed.freedResource(triggerUseTriggerUcrUsageAddress, triggerUseTriggerUcrUsageKey,
        fromAddress, blockingStubFull);

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


