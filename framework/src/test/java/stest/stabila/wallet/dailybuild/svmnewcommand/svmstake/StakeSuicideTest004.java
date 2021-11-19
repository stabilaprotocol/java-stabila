package stest.stabila.wallet.dailybuild.svmnewcommand.svmstake;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Optional;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Account.Cded;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

public class StakeSuicideTest004 {
  private String testFoundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private byte[] testFoundationAddress = PublicMethed.getFinalAddress(testFoundationKey);
  private String testWitnessKey = Configuration.getByPath("testng.conf")
      .getString("witness.key1");
  private String testWitnessKey2 = Configuration.getByPath("testng.conf")
      .getString("witness.key3");
  private byte[] testWitnessAddress = PublicMethed.getFinalAddress(testWitnessKey);
  private byte[] testWitnessAddress2 = PublicMethed.getFinalAddress(testWitnessKey2);



  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] testAddress001 = ecKey1.getAddress();
  String testKey001 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] testAddress002 = ecKey2.getAddress();
  String testKey002 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private byte[] contractAddress;
  String filePath = "src/test/resources/soliditycode/testStakeSuicide.sol";
  String contractName = "testStakeSuicide";
  String code = "";
  String abi = "";

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = false)
  public void beforeClass() {
    System.out.println(testKey001);
    PublicMethed.printAddress(testKey001);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    PublicMethed
        .sendcoin(testAddress001, 1000_000_00000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    code = retMap.get("byteCode").toString();
    abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 1000_000000L, 100,
            null, testKey001, testAddress001, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
  }

  @Test(enabled = false, description = "targetAddress has cded 1,suicide contract stake 1")
  void svmStakeSuicideTest001() {
    ECKey ecKeyTargetAddress = new ECKey(Utils.getRandom());
    byte[] targetAddress = ecKeyTargetAddress.getAddress();
    String testKeyTargetAddress = ByteArray.toHexString(ecKeyTargetAddress.getPrivKeyBytes());
    Assert.assertTrue(PublicMethed
        .sendcoin(targetAddress, 10_000000L, testFoundationAddress, testFoundationKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed
        .cdBalance(targetAddress,1_000000L,3,testKeyTargetAddress,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account targetAccount = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    final Cded targetCdedBefore = targetAccount.getCded(0);
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        1000_000000L, 100, null, testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testWitnessAddress) + "\","  + 1000000;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> ex = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    Assert.assertEquals(ByteArray.toInt(ex.get().getContractResult(0).toByteArray()),1);

    Account ownerAccount = PublicMethed.queryAccount(contractAddress,blockingStubFull);
    final Cded ownerCded = ownerAccount.getCded(0);
    Long ownerBalance = ownerAccount.getBalance();
    String methodStrSuicide = "SelfdestructTest(address)";
    String argsStrSuicide = "\"" + Base58.encode58Check(targetAddress) + "\"";
    String txidSuicide  = PublicMethed
        .triggerContract(contractAddress, methodStrSuicide, argsStrSuicide,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ex = PublicMethed.getTransactionInfoById(txidSuicide, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account targetAccountAfter = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    Cded targetCdedAfter = targetAccountAfter.getCded(0);

    BigInteger expected =
        BigInteger.valueOf(ownerCded.getExpireTime())
            .multiply(BigInteger.valueOf(ownerCded.getCdedBalance()))
            .add(BigInteger.valueOf(targetCdedBefore.getExpireTime())
                .multiply(BigInteger.valueOf(targetCdedBefore.getCdedBalance())))
            .divide(BigInteger.valueOf(ownerCded.getCdedBalance())
                .add(BigInteger.valueOf(targetCdedBefore.getCdedBalance())));

    Assert.assertEquals(expected.longValue(), targetCdedAfter.getExpireTime());
    Assert.assertEquals(targetCdedAfter.getCdedBalance(),
        ownerCded.getCdedBalance() + targetCdedBefore.getCdedBalance());

  }

  @Test(enabled = false, description = "targetAddress has cded 1,suicide contract stake all")
  void svmStakeSuicideTest002() {
    ECKey ecKeyTargetAddress = new ECKey(Utils.getRandom());
    byte[] targetAddress = ecKeyTargetAddress.getAddress();
    String testKeyTargetAddress = ByteArray.toHexString(ecKeyTargetAddress.getPrivKeyBytes());
    Assert.assertTrue(PublicMethed
        .sendcoin(targetAddress, 10_000000L, testFoundationAddress, testFoundationKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed
        .cdBalance(targetAddress,1_000000L,3,testKeyTargetAddress,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account targetAccount = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    final Cded targetCdedBefore = targetAccount.getCded(0);
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        100_000000L, 100, null, testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testWitnessAddress) + "\","  + 100_000000L;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> ex = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    Assert.assertEquals(ByteArray.toInt(ex.get().getContractResult(0).toByteArray()),1);

    Account ownerAccount = PublicMethed.queryAccount(contractAddress,blockingStubFull);
    final Cded ownerCded = ownerAccount.getCded(0);
    Long ownerBalance = ownerAccount.getBalance();
    String methodStrSuicide = "SelfdestructTest(address)";
    String argsStrSuicide = "\"" + Base58.encode58Check(targetAddress) + "\"";
    String txidSuicide  = PublicMethed
        .triggerContract(contractAddress, methodStrSuicide, argsStrSuicide,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ex = PublicMethed.getTransactionInfoById(txidSuicide, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account targetAccountAfter = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    Cded targetCdedAfter = targetAccountAfter.getCded(0);

    BigInteger expected =
        BigInteger.valueOf(ownerCded.getExpireTime())
            .multiply(BigInteger.valueOf(ownerCded.getCdedBalance()))
            .add(BigInteger.valueOf(targetCdedBefore.getExpireTime())
                .multiply(BigInteger.valueOf(targetCdedBefore.getCdedBalance())))
            .divide(BigInteger.valueOf(ownerCded.getCdedBalance())
                .add(BigInteger.valueOf(targetCdedBefore.getCdedBalance())));

    Assert.assertEquals(expected.longValue(), targetCdedAfter.getExpireTime());
    Assert.assertEquals(targetCdedAfter.getCdedBalance(),
        ownerCded.getCdedBalance() + targetCdedBefore.getCdedBalance());

  }

  @Test(enabled = false, description = "targetAddress has cded all,suicide contract stake all")
  void svmStakeSuicideTest003() {
    ECKey ecKeyTargetAddress = new ECKey(Utils.getRandom());
    byte[] targetAddress = ecKeyTargetAddress.getAddress();
    String testKeyTargetAddress = ByteArray.toHexString(ecKeyTargetAddress.getPrivKeyBytes());
    Assert.assertTrue(PublicMethed
        .sendcoin(targetAddress, 20_000000L, testFoundationAddress, testFoundationKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(targetAddress,5_000000L,
        3,1, ByteString.copyFrom(testAddress001),testKeyTargetAddress,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed
        .cdBalance(targetAddress,10_000000L,3,testKeyTargetAddress,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account targetAccount = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    final Cded targetCdedBefore = targetAccount.getCded(0);
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        100_000000L, 100, null, testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testWitnessAddress) + "\","  + 100_000000L;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> ex = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    Assert.assertEquals(ByteArray.toInt(ex.get().getContractResult(0).toByteArray()),1);

    Account ownerAccount = PublicMethed.queryAccount(contractAddress,blockingStubFull);
    final Cded ownerCded = ownerAccount.getCded(0);
    Long ownerBalance = ownerAccount.getBalance();
    String methodStrSuicide = "SelfdestructTest(address)";
    String argsStrSuicide = "\"" + Base58.encode58Check(targetAddress) + "\"";
    String txidSuicide  = PublicMethed
        .triggerContract(contractAddress, methodStrSuicide, argsStrSuicide,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ex = PublicMethed.getTransactionInfoById(txidSuicide, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account targetAccountAfter = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    Cded targetCdedAfter = targetAccountAfter.getCded(0);

    BigInteger expected =
        BigInteger.valueOf(ownerCded.getExpireTime())
            .multiply(BigInteger.valueOf(ownerCded.getCdedBalance()))
            .add(BigInteger.valueOf(targetCdedBefore.getExpireTime())
                .multiply(BigInteger.valueOf(targetCdedBefore.getCdedBalance())))
            .divide(BigInteger.valueOf(ownerCded.getCdedBalance())
                .add(BigInteger.valueOf(targetCdedBefore.getCdedBalance())));

    Assert.assertEquals(expected.longValue(), targetCdedAfter.getExpireTime());
    Assert.assertEquals(targetCdedAfter.getCdedBalance(),
        ownerCded.getCdedBalance() + targetCdedBefore.getCdedBalance());

  }

  @Test(enabled = false, description = "targetAddress is new account ,suicide contract stake all")
  void svmStakeSuicideTest004() {
    ECKey ecKeyTargetAddress = new ECKey(Utils.getRandom());
    byte[] targetAddress = ecKeyTargetAddress.getAddress();
    String testKeyTargetAddress = ByteArray.toHexString(ecKeyTargetAddress.getPrivKeyBytes());
    System.out.println(Base58.encode58Check(targetAddress));

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        100_000000L, 100, null, testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testWitnessAddress) + "\","  + 100_000000L;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> ex = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    Assert.assertEquals(ByteArray.toInt(ex.get().getContractResult(0).toByteArray()),1);

    Account ownerAccount = PublicMethed.queryAccount(contractAddress,blockingStubFull);
    final Cded ownerCded = ownerAccount.getCded(0);
    Long ownerBalance = ownerAccount.getBalance();
    String methodStrSuicide = "SelfdestructTest(address)";
    String argsStrSuicide = "\"" + Base58.encode58Check(targetAddress) + "\"";
    String txidSuicide  = PublicMethed
        .triggerContract(contractAddress, methodStrSuicide, argsStrSuicide,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ex = PublicMethed.getTransactionInfoById(txidSuicide, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account targetAccountAfter = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    Cded targetCdedAfter = targetAccountAfter.getCded(0);


    Assert.assertEquals(ownerCded.getExpireTime(), targetCdedAfter.getExpireTime());
    Assert.assertEquals(targetCdedAfter.getCdedBalance(),
        ownerCded.getCdedBalance());

  }

  @Test(enabled = false, description = "targetAddress cded to other address ,suicide contract "
      + "stake all")
  void svmStakeSuicideTest005() {
    ECKey ecKeyTargetAddress = new ECKey(Utils.getRandom());
    byte[] targetAddress = ecKeyTargetAddress.getAddress();
    ECKey ecKey = new ECKey(Utils.getRandom());
    byte[] address = ecKey.getAddress();
    String testKeyTargetAddress = ByteArray.toHexString(ecKeyTargetAddress.getPrivKeyBytes());
    Assert.assertTrue(PublicMethed
        .sendcoin(targetAddress, 10_000000L, testFoundationAddress, testFoundationKey,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert.assertTrue(PublicMethed.cdBalanceForReceiver(targetAddress,5_000000L,
        3,1, ByteString.copyFrom(testAddress001),testKeyTargetAddress,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    System.out.println("aaaa" + Base58.encode58Check(targetAddress));

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        100_000000L, 100, null, testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testWitnessAddress) + "\","  + 100_000000L;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> ex = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    System.out.println("aaaaa" + Base58.encode58Check(contractAddress));
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    Assert.assertEquals(ByteArray.toInt(ex.get().getContractResult(0).toByteArray()),1);


    Account ownerAccount = PublicMethed.queryAccount(contractAddress,blockingStubFull);
    final Cded ownerCded = ownerAccount.getCded(0);
    Long ownerBalance = ownerAccount.getBalance();
    String methodStrSuicide = "SelfdestructTest(address)";
    String argsStrSuicide = "\"" + Base58.encode58Check(targetAddress) + "\"";
    String txidSuicide  = PublicMethed
        .triggerContract(contractAddress, methodStrSuicide, argsStrSuicide,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account targetAccount = PublicMethed.queryAccount(targetAddress,blockingStubFull);
    final Cded targetCdedAfter = targetAccount.getCded(0);
    ex = PublicMethed.getTransactionInfoById(txidSuicide, blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);
    PublicMethed.waitProduceNextBlock(blockingStubFull);


    Assert.assertEquals(ownerCded.getExpireTime(), targetCdedAfter.getExpireTime());
    Assert.assertEquals(targetCdedAfter.getCdedBalance(),
        ownerCded.getCdedBalance());

  }





}
