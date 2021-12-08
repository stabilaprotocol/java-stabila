package stest.stabila.wallet.dailybuild.svmnewcommand.svmstake;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI.TransactionExtention;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class UnStakeTest001 {

  private String testFoundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private byte[] testFoundationAddress = PublicMethed.getFinalAddress(testFoundationKey);
  private String testExecutiveKey = Configuration.getByPath("testng.conf")
      .getString("executive.key4");
  private byte[] testExecutiveAddress = PublicMethed.getFinalAddress(testExecutiveKey);


  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] testAddress001 = ecKey1.getAddress();
  String testKey001 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ECKey ecKey2 = new ECKey(Utils.getRandom());
  private byte[] testAddress002 = ecKey2.getAddress();
  private String testKey002 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private byte[] contractAddress;

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
    PublicMethed.printAddress(testKey001);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = false, description = "unstake normal")
  public void svmStakeTest001Normal() {
    PublicMethed
        .sendcoin(testAddress001, 1120_000_000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "./src/test/resources/soliditycode/unStake001.sol";
    String contractName = "unStakeTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 1000000000L, 100, null,
            testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\"," + 10000000;
    long balanceBefore = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    String txid = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 1);
    Account account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    long balanceAfter = account.getBalance();
    Assert.assertEquals(balanceAfter, balanceBefore - 10000000);
    long cdedBalance = account.getCded(0).getCdedBalance();
    byte[] voteAddress = account.getVotes(0).getVoteAddress().toByteArray();
    long voteCount = account.getVotes(0).getVoteCount();
    Assert.assertEquals(voteCount, 10);
    Assert.assertEquals(voteAddress, testExecutiveAddress);
    Assert.assertEquals(cdedBalance, 10000000);

    methodStr = "unStake()";
    txid = PublicMethed
        .triggerContract(contractAddress, methodStr, "#", false, 0, maxFeeLimit, testAddress001,
            testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(infoById.toString());
    Assert.assertEquals(0, infoById.get().getResultValue());
    contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 1);
    account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    int cdedCount = account.getCdedCount();
    int votesCount = account.getVotesCount();
    Assert.assertEquals(0, cdedCount);
    Assert.assertEquals(0, votesCount);
    Assert.assertEquals(account.getBalance(), balanceBefore);
  }

  @Test(enabled = false, description = "unstake when no stake")
  public void svmUnstakeTest002NoStake() {
    PublicMethed
        .sendcoin(testAddress001, 1120_000_000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "./src/test/resources/soliditycode/unStake001.sol";
    String contractName = "unStakeTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 1000000000L, 100, null,
            testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "unStake()";
    long balanceBefore = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    String txid = PublicMethed
        .triggerContract(contractAddress, methodStr, "", false, 0, maxFeeLimit, testAddress001,
            testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    Account account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    Assert.assertEquals(account.getBalance(), balanceBefore);
    int contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 0);
    int cdedCount = account.getCdedCount();
    int votesCount = account.getVotesCount();
    Assert.assertEquals(0, cdedCount);
    Assert.assertEquals(0, votesCount);
  }

  @Test(enabled = false, description = "unstake twice")
  public void svmUnstakeTest003UnstakeTwice() {
    PublicMethed
        .sendcoin(testAddress001, 1120_000_000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "./src/test/resources/soliditycode/unStake001.sol";
    String contractName = "unStakeTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 1000000000L, 100, null,
            testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\"," + 10000000;
    long balanceBefore = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    String txid = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 1);
    Account account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    long balanceAfter = account.getBalance();
    Assert.assertEquals(balanceAfter, balanceBefore - 10000000);
    long cdedBalance = account.getCded(0).getCdedBalance();
    byte[] voteAddress = account.getVotes(0).getVoteAddress().toByteArray();
    long voteCount = account.getVotes(0).getVoteCount();
    Assert.assertEquals(voteCount, 10);
    Assert.assertEquals(voteAddress, testExecutiveAddress);
    Assert.assertEquals(cdedBalance, 10000000);

    methodStr = "unStake2()";
    txid = PublicMethed
        .triggerContract(contractAddress, methodStr, "", false, 0, maxFeeLimit, testAddress001,
            testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(infoById.toString());
    Assert.assertEquals(0, infoById.get().getResultValue());
    contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 0);
    account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    int cdedCount = account.getCdedCount();
    int votesCount = account.getVotesCount();
    Assert.assertEquals(0, cdedCount);
    Assert.assertEquals(0, votesCount);
    Assert.assertEquals(account.getBalance(), balanceBefore);
  }

  @Test(enabled = false, description = "unstake revert")
  public void svmUnstakeTest004Revert() {
    PublicMethed
        .sendcoin(testAddress001, 1120_000_000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "./src/test/resources/soliditycode/unStake001.sol";
    String contractName = "unStakeTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 1000000000L, 100, null,
            testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\"," + 10000000;
    long balanceBefore = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    String txid = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 1);
    Account account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    long balanceAfter = account.getBalance();
    Assert.assertEquals(balanceAfter, balanceBefore - 10000000);
    long cdedBalance = account.getCded(0).getCdedBalance();
    byte[] voteAddress = account.getVotes(0).getVoteAddress().toByteArray();
    long voteCount = account.getVotes(0).getVoteCount();
    Assert.assertEquals(voteCount, 10);
    Assert.assertEquals(voteAddress, testExecutiveAddress);
    Assert.assertEquals(cdedBalance, 10000000);

    methodStr = "revertTest2(address)";
    argsStr = "\"" + Base58.encode58Check(testAddress002) + "\"";
    txid = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr, false, 0, maxFeeLimit, testAddress001,
            testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(infoById.toString());
    Assert.assertEquals(0, infoById.get().getResultValue());
    contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 0);
    account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    int cdedCount = account.getCdedCount();
    int votesCount = account.getVotesCount();
    Assert.assertEquals(0, cdedCount);
    Assert.assertEquals(0, votesCount);
    Assert.assertEquals(account.getBalance(), 993000000L);
    long balance = PublicMethed.queryAccount(testAddress002, blockingStubFull).getBalance();
    Assert.assertEquals(7000000L, balance);
  }

  @Test(enabled = false, description = "unstake call another contract in one contract")
  public void svmUnstakeTest005CallAnotherInOneContract() {
    PublicMethed
        .sendcoin(testAddress001, 2120_000_000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "./src/test/resources/soliditycode/unStake001.sol";
    String contractName = "unStakeTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 1000000000L, 100, null,
            testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long callvalue = 1000000000L;
    String txid = PublicMethed.triggerContract(contractAddress, "deployB()", "#", false,
        callvalue, maxFeeLimit, testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(infoById.toString());
    Assert.assertEquals(0, infoById.get().getResultValue());
    String addressHex =
        "41" + ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray())
            .substring(24);
    logger.info("address_hex: " + addressHex);
    byte[] contractAddressB = ByteArray.fromHexString(addressHex);
    logger.info("contractAddressB: " + Base58.encode58Check(contractAddressB));
    long contractAddressBBalance = PublicMethed.queryAccount(contractAddressB, blockingStubFull)
        .getBalance();
    Assert.assertEquals(callvalue, contractAddressBBalance);

    String methodStr = "BStake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\"," + 10000000;
    txid = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    int contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 1);
    Account account = PublicMethed.queryAccount(contractAddressB, blockingStubFull);
    long cdedBalance = account.getCded(0).getCdedBalance();
    byte[] voteAddress = account.getVotes(0).getVoteAddress().toByteArray();
    long voteCount = account.getVotes(0).getVoteCount();
    long balanceAfter = account.getBalance();
    Assert.assertEquals(voteCount, 10);
    Assert.assertEquals(voteAddress, testExecutiveAddress);
    Assert.assertEquals(cdedBalance, 10000000);
    Assert.assertEquals(balanceAfter, contractAddressBBalance - 10000000);
    long contractAddressBalance = PublicMethed.queryAccount(contractAddress, blockingStubFull)
        .getBalance();
    Assert.assertEquals(contractAddressBalance, 1000000000);

    methodStr = "BUnStake()";
    txid = PublicMethed
        .triggerContract(contractAddress, methodStr, "", false, 0, maxFeeLimit, testAddress001,
            testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(infoById.toString());
    Assert.assertEquals(0, infoById.get().getResultValue());
    contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 1);
    account = PublicMethed.queryAccount(contractAddressB, blockingStubFull);
    int cdedCount = account.getCdedCount();
    int votesCount = account.getVotesCount();
    Assert.assertEquals(0, cdedCount);
    Assert.assertEquals(0, votesCount);
    Assert.assertEquals(account.getBalance(), contractAddressBBalance);
    contractAddressBalance = PublicMethed.queryAccount(contractAddress, blockingStubFull)
        .getBalance();
    Assert.assertEquals(contractAddressBalance, 1000000000);
  }

  @Test(enabled = false, description = "unstake with reward balance")
  public void svmUnstakeTest006WithRewardBalance() {
    PublicMethed
        .sendcoin(testAddress001, 1120_000_000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "./src/test/resources/soliditycode/unStake001.sol";
    String contractName = "unStakeTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 1000000000L, 100, null,
            testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\"," + 10000000;
    long balanceBefore = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    String txid = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 1);
    Account account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    long balanceAfter = account.getBalance();
    Assert.assertEquals(balanceAfter, balanceBefore - 10000000);
    long cdedBalance = account.getCded(0).getCdedBalance();
    byte[] voteAddress = account.getVotes(0).getVoteAddress().toByteArray();
    long voteCount = account.getVotes(0).getVoteCount();
    Assert.assertEquals(voteCount, 10);
    Assert.assertEquals(voteAddress, testExecutiveAddress);
    Assert.assertEquals(cdedBalance, 10000000);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    methodStr = "rewardBalance(address)";
    argsStr = "\"" + Base58.encode58Check(contractAddress) + "\"";
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress, methodStr, argsStr, false, 0, 0, "0",
            0, testAddress001, testKey001, blockingStubFull);
    Transaction transaction = transactionExtention.getTransaction();
    byte[] result = transactionExtention.getConstantResult(0).toByteArray();
    System.out.println("message:" + transaction.getRet(0).getRet());
    System.out.println(
        ":" + ByteArray.toStr(transactionExtention.getResult().getMessage().toByteArray()));
    System.out.println("Result:" + Hex.toHexString(result));
    org.junit.Assert.assertEquals(0, ByteArray.toLong(ByteArray
        .fromHexString(Hex
            .toHexString(result))));

    methodStr = "withdrawReward()";
    txid = PublicMethed
        .triggerContract(contractAddress, methodStr, "", false, 0, maxFeeLimit, testAddress001,
            testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(infoById.toString());
    Assert.assertEquals(0, infoById.get().getResultValue());
    contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 0);
    account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    long balanceAfter2 = account.getBalance();
    Assert.assertEquals(balanceAfter, balanceAfter2);

    methodStr = "unStake2()";
    txid = PublicMethed
        .triggerContract(contractAddress, methodStr, "", false, 0, maxFeeLimit, testAddress001,
            testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(infoById.toString());
    Assert.assertEquals(0, infoById.get().getResultValue());
    contractResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult, 0);
    account = PublicMethed.queryAccount(contractAddress, blockingStubFull);
    int cdedCount = account.getCdedCount();
    int votesCount = account.getVotesCount();
    Assert.assertEquals(0, cdedCount);
    Assert.assertEquals(0, votesCount);
    Assert.assertEquals(account.getBalance(), balanceBefore);

    methodStr = "rewardBalance(address)";
    argsStr = "\"" + Base58.encode58Check(contractAddress) + "\"";
    transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress, methodStr, argsStr, false, 0, 0, "0",
            0, testAddress001, testKey001, blockingStubFull);
    transaction = transactionExtention.getTransaction();
    result = transactionExtention.getConstantResult(0).toByteArray();
    System.out.println("message:" + transaction.getRet(0).getRet());
    System.out.println(
        ":" + ByteArray.toStr(transactionExtention.getResult().getMessage().toByteArray()));
    System.out.println("Result:" + Hex.toHexString(result));
    org.junit.Assert.assertEquals(0, ByteArray.toLong(ByteArray
        .fromHexString(Hex
            .toHexString(result))));
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(testAddress001, testKey001, testFoundationAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

}
