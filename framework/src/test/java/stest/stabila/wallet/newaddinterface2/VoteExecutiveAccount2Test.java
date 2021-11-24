package stest.stabila.wallet.newaddinterface2;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.NumberMessage;
import org.stabila.api.GrpcAPI.Return;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.contract.BalanceContract.CdBalanceContract;
import org.stabila.protos.contract.BalanceContract.UncdBalanceContract;
import org.stabila.protos.contract.ExecutiveContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.WalletClient;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;
import stest.stabila.wallet.common.client.utils.TransactionUtils;

@Slf4j
public class VoteExecutiveAccount2Test {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");

  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);

  private ManagedChannel channelFull = null;
  private ManagedChannel searchChannelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletGrpc.WalletBlockingStub searchBlockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String searchFullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

  public static String loadPubKey() {
    char[] buf = new char[0x100];
    return String.valueOf(buf, 32, 130);
  }

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass
  public void beforeClass() {

    WalletClient.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    logger.info("Pre fix byte =====  " + WalletClient.getAddressPreFixByte());
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    searchChannelFull = ManagedChannelBuilder.forTarget(searchFullnode)
        .usePlaintext(true)
        .build();
    searchBlockingStubFull = WalletGrpc.newBlockingStub(searchChannelFull);

  }

  @Test(enabled = true)
  public void testVoteExecutive2() {
    //get account
    ECKey ecKey = new ECKey(Utils.getRandom());
    byte[] lowBalAddress = ecKey.getAddress();
    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] lowBalAddress2 = ecKey2.getAddress();
    String lowBalTest2 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
    //sendcoin
    Return ret1 = PublicMethed.sendcoin2(lowBalAddress, 21245000000L,
        fromAddress, testKey002, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
    ret1 = PublicMethed.sendcoin2(lowBalAddress2, 21245000000L,
        fromAddress, testKey002, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");

    //assetissue
    String createUrl1 = "adfafds";
    byte[] createUrl = createUrl1.getBytes();
    String lowBalTest = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    ret1 = createExecutive2(lowBalAddress, createUrl, lowBalTest);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");

    String voteStr1 = Base58.encode58Check(lowBalAddress);

    //Base58.encode58Check(getFinalAddress(key)；
    //String voteStr = "TB4B1RMhoPeivkj4Hebm6tttHjRY9yQFes";
    String voteStr = voteStr1;
    HashMap<String, String> smallVoteMap = new HashMap<String, String>();
    smallVoteMap.put(voteStr, "1");
    HashMap<String, String> wrongVoteMap = new HashMap<String, String>();
    wrongVoteMap.put(voteStr, "-1");
    HashMap<String, String> zeroVoteMap = new HashMap<String, String>();
    zeroVoteMap.put(voteStr, "0");

    HashMap<String, String> veryLargeMap = new HashMap<String, String>();
    veryLargeMap.put(voteStr, "1000000000");
    HashMap<String, String> wrongDropMap = new HashMap<String, String>();
    wrongDropMap.put(voteStr, "10000000000000000");

    //Vote failed due to no cd balance.
    //Assert.assertFalse(VoteExecutive(smallVoteMap, NO_CDED_ADDRESS, no_cded_balance_testKey));

    //Cd balance to get vote ability.
    ret1 = PublicMethed.cdBalance2(fromAddress, 10000000L, 3L, testKey002, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
    //Vote failed when the vote is large than the cd balance.
    ret1 = voteExecutive2(veryLargeMap, fromAddress, testKey002);
    Assert.assertEquals(ret1.getCode(), Return.response_code.CONTRACT_VALIDATE_ERROR);

    //Vote failed due to 0 vote.
    ret1 = voteExecutive2(zeroVoteMap, fromAddress, testKey002);
    Assert.assertEquals(ret1.getCode(), Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : vote count must be greater than 0");

    ret1 = voteExecutive2(wrongVoteMap, fromAddress, testKey002);
    Assert.assertEquals(ret1.getCode(), Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : vote count must be greater than 0");

    ret1 = voteExecutive2(wrongDropMap, fromAddress, testKey002);
    Assert.assertEquals(ret1.getCode(), Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : overflow: checkedMultiply(10000000000000000, 1000000)");
    ret1 = voteExecutive2(smallVoteMap, fromAddress, testKey002);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");

  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (searchChannelFull != null) {
      searchChannelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /**
   * constructor.
   */

  public Boolean voteExecutive(HashMap<String, String> executive, byte[] addRess, String priKey) {

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account beforeVote = queryAccount(ecKey, blockingStubFull);
    Long beforeVoteNum = 0L;
    if (beforeVote.getVotesCount() != 0) {
      beforeVoteNum = beforeVote.getVotes(0).getVoteCount();
    }

    ExecutiveContract.VoteExecutiveContract.Builder builder = ExecutiveContract.VoteExecutiveContract
        .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(addRess));
    for (String addressBase58 : executive.keySet()) {

      ExecutiveContract.VoteExecutiveContract.Vote.Builder voteBuilder =
          ExecutiveContract.VoteExecutiveContract.Vote
              .newBuilder();
      byte[] address = WalletClient.decodeFromBase58Check(addressBase58);
      logger.info("address ====== " + ByteArray.toHexString(address));
      String value = executive.get(addressBase58);
      if (address == null) {
        continue;
      }
      voteBuilder.setVoteAddress(ByteString.copyFrom(address));
      long count = Long.parseLong(value);
      voteBuilder.setVoteCount(count);
      builder.addVotes(voteBuilder.build());
    }

    ExecutiveContract.VoteExecutiveContract contract = builder.build();

    Transaction transaction = blockingStubFull.voteExecutiveAccount(contract);
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      logger.info("transaction == null");
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    Return response = blockingStubFull.broadcastTransaction(transaction);

    if (!response.getResult()) {
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return false;
    }
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Account afterVote = queryAccount(ecKey, searchBlockingStubFull);
    //Long afterVoteNum = afterVote.getVotes(0).getVoteCount();
    for (String key : executive.keySet()) {
      for (int j = 0; j < afterVote.getVotesCount(); j++) {
        logger.info(Long.toString(Long.parseLong(executive.get(key))));
        logger.info(key);
        if (key.equals("TB4B1RMhoPeivkj4Hebm6tttHjRY9yQFes")) {
          logger.info("catch it");
          logger.info(Long.toString(afterVote.getVotes(j).getVoteCount()));
          logger.info(Long.toString(Long.parseLong(executive.get(key))));
          Assert
              .assertTrue(afterVote.getVotes(j).getVoteCount() == Long.parseLong(executive.get(key)));
        }

      }
    }
    return true;
  }

  /**
   * constructor.
   */

  public GrpcAPI.Return createExecutive2(byte[] owner, byte[] url, String priKey) {
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    ExecutiveContract.ExecutiveCreateContract.Builder builder = ExecutiveContract.ExecutiveCreateContract
        .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setUrl(ByteString.copyFrom(url));
    ExecutiveContract.ExecutiveCreateContract contract = builder.build();

    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull.createExecutive2(contract);

    if (transactionExtention == null) {
      return transactionExtention.getResult();
    }
    GrpcAPI.Return ret = transactionExtention.getResult();
    if (!ret.getResult()) {
      System.out.println("Code = " + ret.getCode());
      System.out.println("Message = " + ret.getMessage().toStringUtf8());
      return ret;
    } else {
      System.out.println("Code = " + ret.getCode());
      System.out.println("Message = " + ret.getMessage().toStringUtf8());
    }
    Protocol.Transaction transaction = transactionExtention.getTransaction();
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      System.out.println("Transaction is empty");
      return transactionExtention.getResult();
    }
    System.out.println(
        "Receive txid = " + ByteArray.toHexString(transactionExtention.getTxid().toByteArray()));

    transaction = signTransaction(ecKey, transaction);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      return response;
    }
    return ret;

  }

  /**
   * constructor.
   */

  public Return voteExecutive2(HashMap<String, String> executive, byte[] addRess, String priKey) {

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account beforeVote = queryAccount(ecKey, blockingStubFull);
    Long beforeVoteNum = 0L;
    if (beforeVote.getVotesCount() != 0) {
      beforeVoteNum = beforeVote.getVotes(0).getVoteCount();
    }

    ExecutiveContract.VoteExecutiveContract.Builder builder = ExecutiveContract.VoteExecutiveContract
        .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(addRess));
    for (String addressBase58 : executive.keySet()) {

      ExecutiveContract.VoteExecutiveContract.Vote.Builder voteBuilder =
          ExecutiveContract.VoteExecutiveContract.Vote
              .newBuilder();
      byte[] address = WalletClient.decodeFromBase58Check(addressBase58);
      logger.info("address ====== " + ByteArray.toHexString(address));
      String value = executive.get(addressBase58);
      if (address == null) {
        continue;
      }
      voteBuilder.setVoteAddress(ByteString.copyFrom(address));
      long count = Long.parseLong(value);
      voteBuilder.setVoteCount(count);
      builder.addVotes(voteBuilder.build());
    }

    ExecutiveContract.VoteExecutiveContract contract = builder.build();

    //Transaction transaction = blockingStubFull.voteExecutiveAccount(contract);
    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull
        .voteExecutiveAccount2(contract);

    if (transactionExtention == null) {
      return transactionExtention.getResult();
    }
    Return ret = transactionExtention.getResult();
    if (!ret.getResult()) {
      System.out.println("Code = " + ret.getCode());
      System.out.println("Message = " + ret.getMessage().toStringUtf8());
      return ret;
    } else {
      System.out.println("Code = " + ret.getCode());
      System.out.println("Message = " + ret.getMessage().toStringUtf8());
    }
    Transaction transaction = transactionExtention.getTransaction();
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      System.out.println("Transaction is empty");
      return transactionExtention.getResult();
    }
    System.out.println(
        "Receive txid = " + ByteArray.toHexString(transactionExtention.getTxid().toByteArray()));

    transaction = signTransaction(ecKey, transaction);
    Return response = blockingStubFull.broadcastTransaction(transaction);

    if (response.getResult() == false) {
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return response;
    }
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Account afterVote = queryAccount(ecKey, searchBlockingStubFull);
    //Long afterVoteNum = afterVote.getVotes(0).getVoteCount();
    for (String key : executive.keySet()) {
      for (int j = 0; j < afterVote.getVotesCount(); j++) {
        logger.info(Long.toString(Long.parseLong(executive.get(key))));
        logger.info(key);
        if (key.equals("TB4B1RMhoPeivkj4Hebm6tttHjRY9yQFes")) {
          logger.info("catch it");
          logger.info(Long.toString(afterVote.getVotes(j).getVoteCount()));
          logger.info(Long.toString(Long.parseLong(executive.get(key))));
          Assert
              .assertTrue(afterVote.getVotes(j).getVoteCount() == Long.parseLong(executive.get(key)));
        }

      }
    }
    return ret;
  }

  /**
   * constructor.
   */

  public Boolean cdBalance(byte[] addRess, long cdBalance, long cdDuration,
      String priKey) {
    byte[] address = addRess;
    long cdedBalance = cdBalance;
    long cdedDuration = cdDuration;

    //String priKey = testKey002;
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account beforeFronzen = queryAccount(ecKey, blockingStubFull);

    Long beforeCdedBalance = 0L;
    //Long beforeBandwidth     = beforeFronzen.getBandwidth();
    if (beforeFronzen.getCdedCount() != 0) {
      beforeCdedBalance = beforeFronzen.getCded(0).getCdedBalance();
      //beforeBandwidth     = beforeFronzen.getBandwidth();
      //logger.info(Long.toString(beforeFronzen.getBandwidth()));
      logger.info(Long.toString(beforeFronzen.getCded(0).getCdedBalance()));
    }

    CdBalanceContract.Builder builder = CdBalanceContract.newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess).setCdedBalance(cdedBalance)
        .setCdedDuration(cdedDuration);

    CdBalanceContract contract = builder.build();

    Transaction transaction = blockingStubFull.cdBalance(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    Return response = blockingStubFull.broadcastTransaction(transaction);

    if (response.getResult() == false) {
      return false;
    }

    Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    Block searchCurrentBlock = searchBlockingStubFull.getNowBlock(GrpcAPI
        .EmptyMessage.newBuilder().build());
    Integer wait = 0;
    while (searchCurrentBlock.getBlockHeader().getRawData().getNumber()
        < currentBlock.getBlockHeader().getRawData().getNumber() + 1 && wait < 30) {
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      logger.info("Another fullnode didn't syn the first fullnode data");
      searchCurrentBlock = searchBlockingStubFull.getNowBlock(GrpcAPI
          .EmptyMessage.newBuilder().build());
      wait++;
      if (wait == 9) {
        logger.info("Didn't syn,skip to next case.");
      }
    }

    Account afterFronzen = queryAccount(ecKey, searchBlockingStubFull);
    Long afterCdedBalance = afterFronzen.getCded(0).getCdedBalance();
    //Long afterBandwidth     = afterFronzen.getBandwidth();
    //logger.info(Long.toString(afterFronzen.getBandwidth()));
    //logger.info(Long.toString(afterFronzen.getCded(0).getCdedBalance()));
    //logger.info(Integer.toString(search.getCdedCount()));
    logger.info(
        "aftercdedbalance =" + Long.toString(afterCdedBalance) + "beforecdedbalance =  "
            + beforeCdedBalance + "cdbalance = " + Long.toString(cdBalance));
    //logger.info("afterbandwidth = " + Long.toString(afterBandwidth) + " beforebandwidth =
    // " + Long.toString(beforeBandwidth));
    //if ((afterCdedBalance - beforeCdedBalance != cdBalance) ||
    //       (cdBalance * cded_duration -(afterBandwidth - beforeBandwidth) !=0)){
    //  logger.info("After 20 second, two node still not synchronous");
    // }
    Assert.assertTrue(afterCdedBalance - beforeCdedBalance == cdBalance);
    //Assert.assertTrue(cdBalance * cded_duration - (afterBandwidth -
    // beforeBandwidth) <= 1000000);
    return true;


  }

  /**
   * constructor.
   */

  public boolean unCdBalance(byte[] addRess, String priKey) {
    byte[] address = addRess;

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account search = queryAccount(ecKey, blockingStubFull);

    UncdBalanceContract.Builder builder = UncdBalanceContract
        .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess);

    UncdBalanceContract contract = builder.build();

    Transaction transaction = blockingStubFull.uncdBalance(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    Return response = blockingStubFull.broadcastTransaction(transaction);
    return response.getResult();
  }

  /**
   * constructor.
   */

  public Account queryAccount(ECKey ecKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
    byte[] address;
    if (ecKey == null) {
      String pubKey = loadPubKey(); //04 PubKey[128]
      if (StringUtils.isEmpty(pubKey)) {
        logger.warn("Warning: QueryAccount failed, no wallet address !!");
        return null;
      }
      byte[] pubKeyAsc = pubKey.getBytes();
      byte[] pubKeyHex = Hex.decode(pubKeyAsc);
      ecKey = ECKey.fromPublicOnly(pubKeyHex);
    }
    return grpcQueryAccount(ecKey.getAddress(), blockingStubFull);
  }

  public byte[] getAddress(ECKey ecKey) {
    return ecKey.getAddress();
  }

  /**
   * constructor.
   */

  public Account grpcQueryAccount(byte[] address, WalletGrpc.WalletBlockingStub blockingStubFull) {
    ByteString addressBs = ByteString.copyFrom(address);
    Account request = Account.newBuilder().setAddress(addressBs).build();
    return blockingStubFull.getAccount(request);
  }

  /**
   * constructor.
   */

  public Block getBlock(long blockNum, WalletGrpc.WalletBlockingStub blockingStubFull) {
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    builder.setNum(blockNum);
    return blockingStubFull.getBlockByNum(builder.build());

  }

  private Transaction signTransaction(ECKey ecKey, Transaction transaction) {
    if (ecKey == null || ecKey.getPrivKey() == null) {
      logger.warn("Warning: Can't sign,there is no private key !!");
      return null;
    }
    transaction = TransactionUtils.setTimestamp(transaction);
    return TransactionUtils.sign(transaction, ecKey);
  }
}


