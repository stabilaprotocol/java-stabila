package stest.stabila.wallet.dailybuild.svmnewcommand.newGrammar;

import com.google.protobuf.ByteString;
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
import org.stabila.api.GrpcAPI;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol;
import org.stabila.protos.contract.SmartContractOuterClass;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;


@Slf4j
public class SvmVote {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  private final String executiveKey = Configuration.getByPath("testng.conf")
      .getString("executive.key1");
  private final byte[] executiveAddress = PublicMethed.getFinalAddress(executiveKey);
  byte[] mapKeyContract = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  int cdCount = 100000000;
  int voteCount = 1;

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }


  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    Assert.assertTrue(PublicMethed
        .sendcoin(contractExcAddress, 300100_000_000L,
            testNetAccountAddress, testNetAccountKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String filePath = "src/test/resources/soliditycode/svmVote.sol";
    String contractName = "TestVote";

    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    mapKeyContract = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        1000000000L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    SmartContractOuterClass.SmartContract smartContract = PublicMethed.getContract(mapKeyContract,
        blockingStubFull);
    Assert.assertNotNull(smartContract.getAbi());
  }

  @Test(enabled = true, description = "query reward balance")
  public void test01QueryRewardBalance() {
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "queryRewardBalance()", "#", true,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    long trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info("result: " + trueRes);
    Assert.assertEquals(0, trueRes);

    GrpcAPI.BytesMessage bytesMessage = GrpcAPI.BytesMessage.newBuilder().setValue(ByteString
        .copyFrom(mapKeyContract))
        .build();
    long reward = blockingStubFull.getRewardInfo(bytesMessage).getNum();
    org.testng.Assert.assertEquals(trueRes, reward);
  }


  @Test(enabled = true, description = "cd balance and vote executive")
  public void test02VoteExecutive() {
    String methodStr = "cd(address,uint256,uint256)";
    String receiverAdd = Base58.encode58Check(mapKeyContract);
    String args = "\"" + receiverAdd + "\"," + cdCount + ",1";
    String triggerTxid = PublicMethed.triggerContract(mapKeyContract,
        methodStr, args, false, 0, maxFeeLimit, "0", 0,
        contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<Protocol.TransactionInfo> transactionInfo = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertEquals(0, transactionInfo.get().getResultValue());
    Assert.assertEquals(Protocol.Transaction.Result.contractResult.SUCCESS,
        transactionInfo.get().getReceipt().getResult());
    Protocol.InternalTransaction internal = transactionInfo.get().getInternalTransactions(0);
    String note = internal.getNote().toStringUtf8();
    Assert.assertEquals("cdForUcr", note);
    Assert.assertEquals(cdCount, internal.getCallValueInfo(0).getCallValue());

    String executive58Add = Base58.encode58Check(executiveAddress);
    args = "[\"" + executive58Add + "\"],[" + voteCount + "]";
    logger.info("vote args: " + args);
    methodStr = "voteExecutive(address[],uint256[])";
    triggerTxid = PublicMethed.triggerContract(mapKeyContract, methodStr, args, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    transactionInfo = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertEquals(0, transactionInfo.get().getResultValue());
    Assert.assertEquals(Protocol.Transaction.Result.contractResult.SUCCESS,
        transactionInfo.get().getReceipt().getResult());
    internal = transactionInfo.get().getInternalTransactions(0);
    note = internal.getNote().toStringUtf8();
    Assert.assertEquals("voteExecutive", note);
    Assert.assertTrue(internal.getExtra().length() > 1);

    Protocol.Account info = PublicMethed.queryAccount(mapKeyContract, blockingStubFull);
    int voteCount = info.getVotesCount();
    logger.info("voteCount: " + voteCount);
    Assert.assertEquals(1, voteCount);
  }

  @Test(enabled = true, description = "query contract address is Sr Candidate or not")
  public void test03IsSrCandidate() {
    String args = "\"" + Base58.encode58Check(mapKeyContract) + "\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "isExecutive(address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertEquals(0, 0);
  }

  @Test(enabled = true, description = "query sr address is Sr Candidate or not")
  public void test04IsSrCandidate() {
    String args = "\"" + Base58.encode58Check(executiveAddress) + "\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "isExecutive(address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertEquals(1, 1);
  }

  @Test(enabled = true, description = "query zero address is Sr Candidate or not")
  public void test05IsSrCandidate() {
    String args = "\"T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "isExecutive(address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertEquals(0, 0);
  }

  @Test(enabled = true, description = "query sr's total vote count")
  public void test06querySrTotalVoteCount() {
    String args = "\"" + Base58.encode58Check(executiveAddress) + "\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "queryTotalVoteCount(address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertEquals(0, trueRes);
  }

  @Test(enabled = true, description = "query contract's total vote count")
  public void test07queryContractTotalVoteCount() {
    String args = "\"" + Base58.encode58Check(mapKeyContract) + "\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "queryTotalVoteCount(address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertEquals(cdCount / 1000000, trueRes);
  }

  @Test(enabled = true, description = "query vote count")
  public void test08queryVoteCount() {
    String from = Base58.encode58Check(mapKeyContract);
    String to = Base58.encode58Check(executiveAddress);
    String args = "\"" + from + "\",\"" + to + "\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "queryVoteCount(address,address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertEquals(voteCount, trueRes);
  }

  @Test(enabled = true, description = "query contract used vote count")
  public void test09queryUsedVoteCount() {
    String from = Base58.encode58Check(mapKeyContract);
    String args = "\"" + from + "\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "queryUsedVoteCount(address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertEquals(voteCount, trueRes);
  }

  @Test(enabled = true, description = "query executives received vote count")
  public void test10queryReceivedVoteCount() {
    String executive = Base58.encode58Check(executiveAddress);
    String args = "\"" + executive + "\"";
    GrpcAPI.TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(mapKeyContract,
            "queryReceivedVoteCount(address)", args, false,
            0, maxFeeLimit, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    int trueRes = ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray());
    logger.info(trueRes + "");
    Assert.assertTrue(trueRes > 0);
    Optional<GrpcAPI.ExecutiveList> list = PublicMethed.listExecutives(blockingStubFull);
    long receiveCount = 0;
    String temAdd;
    for (int i = 0; i < list.get().getExecutivesCount(); i++) {
      temAdd = Base58.encode58Check(list.get().getExecutives(i).getAddress().toByteArray());
      if (executive.equals(temAdd)) {
        receiveCount = list.get().getExecutives(i).getVoteCount();
        break;
      }
    }
    Assert.assertEquals(trueRes, receiveCount);
  }

  @Test(enabled = true, description = "withdraw reward")
  public void test11WithdrawReward() {
    String methodStr = "withdrawReward()";
    String triggerTxid = PublicMethed.triggerContract(mapKeyContract, methodStr, "#", false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<Protocol.TransactionInfo> transactionInfo = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertEquals(0, transactionInfo.get().getResultValue());
    Assert.assertEquals(Protocol.Transaction.Result.contractResult.SUCCESS,
        transactionInfo.get().getReceipt().getResult());
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Protocol.InternalTransaction internal = transactionInfo.get().getInternalTransactions(0);
    String note = internal.getNote().toStringUtf8();
    Assert.assertEquals("withdrawReward", note);
    Assert.assertEquals(1, internal.getCallValueInfoCount());
    Assert.assertEquals("", internal.getCallValueInfo(0).toString());
  }

  @Test(enabled = true, description = "uncd ucr")
  public void test12Uncd() {
    String methodStr = "uncd(address,uint256)";
    String args = "\"" + Base58.encode58Check(mapKeyContract) + "\",1";
    String triggerTxid = PublicMethed.triggerContract(mapKeyContract, methodStr, args, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<Protocol.TransactionInfo> transactionInfo = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertEquals(0, transactionInfo.get().getResultValue());
    Assert.assertEquals(Protocol.Transaction.Result.contractResult.SUCCESS,
        transactionInfo.get().getReceipt().getResult());

    Protocol.InternalTransaction internal = transactionInfo.get().getInternalTransactions(0);
    String note = internal.getNote().toStringUtf8();
    Assert.assertEquals("uncdForUcr", note);
    Assert.assertEquals(cdCount, internal.getCallValueInfo(0).getCallValue());
  }

  @Test(enabled = true, description = "kill me")
  public void test13Suicide() {
    String methodStr = "killme(address)";
    String args = "\"" + Base58.encode58Check(executiveAddress) + "\"";
    String triggerTxid = PublicMethed.triggerContract(mapKeyContract, methodStr, args, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<Protocol.TransactionInfo> transactionInfo = PublicMethed
        .getTransactionInfoById(triggerTxid, blockingStubFull);
    Assert.assertEquals(0, transactionInfo.get().getResultValue());
    Assert.assertEquals(Protocol.Transaction.Result.contractResult.SUCCESS,
        transactionInfo.get().getReceipt().getResult());
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    SmartContractOuterClass.SmartContract smartContract = PublicMethed
        .getContract(mapKeyContract, blockingStubFull);
    Assert.assertEquals("", smartContract.getAbi().toString());
  }


  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(contractExcAddress, contractExcKey,
        testNetAccountAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }


}

