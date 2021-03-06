package stest.stabila.wallet.dailybuild.svmnewcommand.svmstake;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
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
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class StakeTest001 {
  private String testFoundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private byte[] testFoundationAddress = PublicMethed.getFinalAddress(testFoundationKey);
  private String testExecutiveKey = Configuration.getByPath("testng.conf")
      .getString("executive.key1");
  private String testExecutiveKey2 = Configuration.getByPath("testng.conf")
      .getString("executive.key3");
  private byte[] testExecutiveAddress = PublicMethed.getFinalAddress(testExecutiveKey);
  private byte[] testExecutiveAddress2 = PublicMethed.getFinalAddress(testExecutiveKey2);



  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] testAddress001 = ecKey1.getAddress();
  String testKey001 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
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

    PublicMethed
        .sendcoin(testAddress001, 1000_000_00000L, testFoundationAddress, testFoundationKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "src/test/resources/soliditycode/testStakeSuicide.sol";
    String contractName = "testStakeSuicide";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        1000_000_0000L, 100, null, testKey001, testAddress001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
  }

  @Test(enabled = false, description = "Vote for executive")
  void svmStakeTest001() {
    long balanceBefore = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\","  + 1000000;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult,1);

    Account request = Account.newBuilder().setAddress(ByteString.copyFrom(contractAddress)).build();
    long balanceAfter = PublicMethed.queryAccount(contractAddress, blockingStubFull).getBalance();
    Assert.assertEquals(balanceAfter,balanceBefore - 1000000);
    byte[] voteAddress = (blockingStubFull.getAccount(request).getVotesList().get(0)
        .getVoteAddress().toByteArray());
    Assert.assertEquals(testExecutiveAddress,voteAddress);
    Assert.assertEquals(1,blockingStubFull.getAccount(request).getVotes(0).getVoteCount());



  }

  @Test(enabled = false, description = "Non-executive account")
  void svmStakeTest002() {
    //account address
    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testAddress001) + "\","  + 1000000;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult,0);

    //contract address
    methodStr = "Stake(address,uint256)";
    argsStr = "\"" + Base58.encode58Check(contractAddress) + "\","  + 1000000;
    txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult,0);


  }


  @Test(enabled = false, description = "Number of votes over balance")
  void svmStakeTest003() {
    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\","  + Long.MAX_VALUE;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());

    Assert.assertEquals(contractResult,0);

  }


  @Test(enabled = false, description = "Enough votes for a second ballot")
  void svmStakeTest004() {

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\","  + 21000000;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult,1);
    Account request = Account.newBuilder().setAddress(ByteString.copyFrom(contractAddress)).build();
    byte[] voteAddress = (blockingStubFull.getAccount(request).getVotesList().get(0)
        .getVoteAddress().toByteArray());
    Assert.assertEquals(testExecutiveAddress,voteAddress);
    System.out.println(blockingStubFull.getAccount(request).getVotesCount());
    Assert.assertEquals(21,blockingStubFull.getAccount(request).getVotes(0).getVoteCount());

    argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\","  + 11000000;
    txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult,1);
    request = Account.newBuilder().setAddress(ByteString.copyFrom(contractAddress)).build();
    voteAddress = (blockingStubFull.getAccount(request).getVotesList().get(0).getVoteAddress()
        .toByteArray());
    Assert.assertEquals(testExecutiveAddress,voteAddress);
    Assert.assertEquals(11,blockingStubFull.getAccount(request).getVotes(0).getVoteCount());

  }


  @Test(enabled = false, description = "Revert test")
  void svmStakeTest005() {

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "revertTest1(address,uint256,address)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\","  + 1000000 + ",\""
        + Base58.encode58Check(testAddress001) + "\"";
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());

    Assert.assertEquals(contractResult,0);

  }


  @Test(enabled = false, description = "Contract Call Contract stake")
  void svmStakeTest006() {
    String methodStr = "deployB()";
    String argsStr = "";
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("txid:" + txid);

    methodStr = "BStake(address,uint256)";
    argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\","  + 1000000;
    txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long callvalue = 1000000000L;
    txid = PublicMethed.triggerContract(contractAddress, "deployB()", "#", false,
        callvalue, maxFeeLimit, testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    String addressHex =
        "41" + ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray())
            .substring(24);
    byte[] contractAddressB = ByteArray.fromHexString(addressHex);
    long contractAddressBBalance = PublicMethed.queryAccount(contractAddressB, blockingStubFull)
        .getBalance();
    Assert.assertEquals(callvalue, contractAddressBBalance);

    methodStr = "BStake(address,uint256)";
    argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\"," + 10000000;
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

  }

  @Test(enabled = false, description = "Vote for the first executive and then vote for the second "
      + "executive.")
  void svmStakeTest007() {

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String methodStr = "Stake(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testExecutiveAddress) + "\","  + 21000000;
    String txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    int contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult,1);
    Account request = Account.newBuilder().setAddress(ByteString.copyFrom(contractAddress)).build();
    byte[] voteAddress = (blockingStubFull.getAccount(request).getVotesList().get(0)
        .getVoteAddress().toByteArray());
    Assert.assertEquals(testExecutiveAddress,voteAddress);
    System.out.println(blockingStubFull.getAccount(request).getVotesCount());
    Assert.assertEquals(21,blockingStubFull.getAccount(request).getVotes(0).getVoteCount());

    argsStr = "\"" + Base58.encode58Check(testExecutiveAddress2) + "\","  + 11000000;
    txid  = PublicMethed
        .triggerContract(contractAddress, methodStr, argsStr,
            false, 0, maxFeeLimit,
            testAddress001, testKey001, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    info =  PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    contractResult = ByteArray.toInt(info.get().getContractResult(0).toByteArray());
    Assert.assertEquals(contractResult,1);
    request = Account.newBuilder().setAddress(ByteString.copyFrom(contractAddress)).build();
    voteAddress = (blockingStubFull.getAccount(request).getVotesList().get(0).getVoteAddress()
        .toByteArray());
    Assert.assertEquals(testExecutiveAddress2,voteAddress);
    Assert.assertEquals(11,blockingStubFull.getAccount(request).getVotes(0).getVoteCount());

  }

}

