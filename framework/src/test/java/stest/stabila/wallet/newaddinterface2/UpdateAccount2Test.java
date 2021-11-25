package stest.stabila.wallet.newaddinterface2;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
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
import org.stabila.api.GrpcAPI.ExecutiveList;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.contract.AccountContract.AccountUpdateContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass;
import org.stabila.protos.contract.BalanceContract;
import org.stabila.protos.contract.ExecutiveContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.WalletClient;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;
import stest.stabila.wallet.common.client.utils.TransactionUtils;

//import stest.stabila.wallet.common.client.AccountComparator;

@Slf4j
public class UpdateAccount2Test {

  private static final long now = System.currentTimeMillis();
  private static final String name = "testAssetIssue_" + Long.toString(now);
  private static final long TotalSupply = now;
  //testng001、testng002、testng003、testng004
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  String mostLongNamePlusOneChar = "1abcdeabcdefabcdefg1abcdefg10o0og1abcdefg10o0oabcd"
      + "efabcdefg1abcdefg10o0og1abcdefg10o0oabcdefabcdefg1abcdefg10o0og1abcdefg10o0oab"
      + "cdefabcdefg1abcdefg10o0og1abcdefg10o0ofabcdefg1abcdefg10o0og1abcdefg10o0o";
  String mostLongName = "abcdeabcdefabcdefg1abcdefg10o0og1abcdefg10o0oabcd"
      + "efabcdefg1abcdefg10o0og1abcdefg10o0oabcdefabcdefg1abcdefg10o0og1abcdefg10o0oab"
      + "cdefabcdefg1abcdefg10o0og1abcdefg10o0ofabcdefg1abcdefg10o0og1abcdefg10o0o";
  String description = "just-test";
  String url = "https://github.com/stabilaprotocol/wallet-cli/";

  //get account
  ECKey ecKey = new ECKey(Utils.getRandom());
  byte[] lowBalAddress = ecKey.getAddress();
  String lowBalTest = ByteArray.toHexString(ecKey.getPrivKeyBytes());

  //System.out.println();
  //get account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] noBandwitchAddress = ecKey1.getAddress();
  String noBandwitch = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

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
    PublicMethed.printAddress(lowBalTest);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test
  public void testCreateAccount2() {
    Account noCreateAccount = queryAccount(lowBalTest, blockingStubFull);
    if (noCreateAccount.getAccountName().isEmpty()) {
      Assert.assertTrue(PublicMethed.cdBalance(fromAddress, 10000000, 3, testKey002,
          blockingStubFull));
      //Assert.assertTrue(sendCoin2(lowBalAddress, 1L, fromAddress, testKey002));
      GrpcAPI.Return ret1 = sendCoin2(lowBalAddress, 1000000L, fromAddress, testKey002);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.SUCCESS);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");

      //Assert.assertTrue(Sendcoin(Low_Bal_ADDRESS, 1000000L, fromAddress, testKey002));
      noCreateAccount = queryAccount(lowBalTest, blockingStubFull);
      logger.info(Long.toString(noCreateAccount.getBalance()));
      //Assert.assertTrue(noCreateAccount.getBalance() == 1);

      //TestVoteToNonExecutiveAccount
      String voteStr = Base58.encode58Check(lowBalAddress);

      HashMap<String, String> voteToNonExecutiveAccount = new HashMap<String, String>();
      voteToNonExecutiveAccount.put(voteStr, "3");

      HashMap<String, String> voteToInvaildAddress = new HashMap<String, String>();
      voteToInvaildAddress.put("27cu1ozb4mX3m2afY68FSAqn3HmMp815d48SS", "4");

      //TQkJsN2Q2sZV9H2dQ5x2rSneKNyLQgegVv
      ECKey ecKey2 = new ECKey(Utils.getRandom());
      byte[] lowBalAddress2 = ecKey2.getAddress();

      ret1 = PublicMethed.sendcoin2(lowBalAddress2, 21245000000L,
          fromAddress, testKey002, blockingStubFull);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.SUCCESS);

      ExecutiveList executivelist = blockingStubFull
          .listExecutives(GrpcAPI.EmptyMessage.newBuilder().build());
      Optional<ExecutiveList> result = Optional.ofNullable(executivelist);
      ExecutiveList executiveList = result.get();
      if (result.get().getExecutivesCount() < 6) {
        String createUrl1 = "adfafds";
        byte[] createUrl = createUrl1.getBytes();
        String lowBalTest2 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
        ret1 = createExecutive2(lowBalAddress2, createUrl, lowBalTest2);
        Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.SUCCESS);
        Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
        String voteStr1 = Base58.encode58Check(lowBalAddress2);
        HashMap<String, String> voteToWitAddress = new HashMap<String, String>();
        voteToWitAddress.put(voteStr1, "1");
        PublicMethed.printAddress(lowBalTest);
        ret1 = voteExecutive2(voteToWitAddress, fromAddress, testKey002);
        Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.SUCCESS);
        Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
        //logger.info("vote to non executive account ok!!!");
      }

      //normal cdBalance
      //Assert.assertTrue(cdBalance2(fromAddress, 10000000L, 3L, testKey002))
      ret1 = cdBalance2(fromAddress, 100000000L, 3L, testKey002);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.SUCCESS);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");

      //vote To NonExecutiveAccount
      ret1 = voteExecutive2(voteToNonExecutiveAccount, fromAddress, testKey002);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
      //vote to InvaildAddress
      ret1 = voteExecutive2(voteToInvaildAddress, fromAddress, testKey002);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(),
          "contract validate error : VoteNumber must more than 0");

    } else {
      logger.info(
          "Please confirm wither the create account test is pass, or you will do it by manual");
    }
  }

  @Test(enabled = true)
  public void testUpdateAccount2() {
    Account tryToUpdateAccount = queryAccount(lowBalTest, blockingStubFull);
    if (tryToUpdateAccount.getAccountName().isEmpty()) {
      GrpcAPI.Return ret1 = updateAccount2(lowBalAddress, mostLongNamePlusOneChar.getBytes(),
          lowBalTest);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(),
          "contract validate error : Invalid accountName");

      ret1 = updateAccount2(lowBalAddress, "".getBytes(), lowBalTest);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(),
          "contract validate error : This name has existed");

      System.out.println("dingwei2:");
      ret1 = updateAccount2(lowBalAddress, mostLongName.getBytes(), lowBalTest);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(),
          "contract validate error : This name has existed");

      ret1 = updateAccount2(lowBalAddress, "secondUpdateName".getBytes(), lowBalTest);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(),
          "contract validate error : This name has existed");

    }
  }

  @Test(enabled = true)
  public void testNoBalanceCreateAssetIssue2() {
    Account lowaccount = queryAccount(lowBalTest, blockingStubFull);
    if (lowaccount.getBalance() > 0) {
      Assert.assertTrue(sendCoin(toAddress, lowaccount.getBalance(), lowBalAddress, lowBalTest));
    }

    System.out.println("1111112222");
    GrpcAPI.Return ret1 = PublicMethed.createAssetIssue2(lowBalAddress, name, TotalSupply, 1, 1,
        now + 100000000L, now + 10000000000L, 2, description, url, 10000L,
        10000L, 1L, 1L, lowBalTest, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : No enough balance for fee!");
    logger.info("nobalancecreateassetissue");
  }

  @Test(enabled = true)
  public void testNoBalanceTransferStb2() {
    //Send Coin failed when there is no enough balance.
    Assert.assertFalse(sendCoin(toAddress, 100000000000000000L, lowBalAddress, lowBalTest));
  }

  @Test(enabled = true)
  public void testNoBalanceCreateExecutive2() {
    //Apply to be super executive failed when no enough balance.
    //Assert.assertFalse(createExecutive2(lowBalAddress, fromAddress, lowBalTest));
    System.out.println("1111222333:" + lowBalAddress);
    GrpcAPI.Return ret1 = createExecutive2(lowBalAddress, fromAddress, lowBalTest);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : balance < AccountUpgradeCost");

  }

  @Test(enabled = true)
  public void testNoCdBalanceToUncdBalance2() {
    //Uncd account failed when no cd balance
    Account noCdAccount = queryAccount(lowBalTest, blockingStubFull);
    if (noCdAccount.getCdedCount() == 0) {
      GrpcAPI.Return ret1 = unCdBalance2(lowBalAddress, lowBalTest);
      Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
      Assert.assertEquals(ret1.getMessage().toStringUtf8(),
          "contract validate error : no cdedBalance");
    } else {
      logger.info("This account has cd balance, please test this case for manual");
    }
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

  /**
   * constructor.
   */

  public Boolean createExecutive(byte[] owner, byte[] url, String priKey) {
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
    Protocol.Transaction transaction = blockingStubFull.createExecutive(contract);
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    return response.getResult();
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
    if (!response.getResult()) {
      return response;
    }
    return ret;
  }

  /**
   * constructor.
   */

  public Boolean sendCoin(byte[] to, long amount, byte[] owner, String priKey) {
    //String priKey = testKey002;
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    BalanceContract.TransferContract.Builder builder = BalanceContract.TransferContract
        .newBuilder();
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsOwner = ByteString.copyFrom(owner);
    builder.setToAddress(bsTo);
    builder.setOwnerAddress(bsOwner);
    builder.setAmount(amount);

    BalanceContract.TransferContract contract = builder.build();
    Protocol.Transaction transaction = blockingStubFull.createTransaction(contract);
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      logger.info("transaction == null");
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    if (!response.getResult()) {
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
    }
    return response.getResult();
  }

  /**
   * constructor.
   */

  public GrpcAPI.Return sendCoin2(byte[] to, long amount, byte[] owner, String priKey) {
    //String priKey = testKey002;
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    BalanceContract.TransferContract.Builder builder = BalanceContract.TransferContract
        .newBuilder();
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsOwner = ByteString.copyFrom(owner);
    builder.setToAddress(bsTo);
    builder.setOwnerAddress(bsOwner);
    builder.setAmount(amount);

    BalanceContract.TransferContract contract = builder.build();
    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull
        .createTransaction2(contract);
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
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return response;
    }
    return ret;
  }

  /**
   * constructor.
   */

  public Boolean createAssetIssue(byte[] address, String name, Long totalSupply, Integer stbNum,
      Integer icoNum, Long startTime, Long endTime,
      Integer voteScore, String description, String url, String priKey) {
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;

    try {
      AssetIssueContractOuterClass.AssetIssueContract.Builder builder =
          AssetIssueContractOuterClass.AssetIssueContract
              .newBuilder();
      builder.setOwnerAddress(ByteString.copyFrom(address));
      builder.setName(ByteString.copyFrom(name.getBytes()));
      builder.setTotalSupply(TotalSupply);
      builder.setStbNum(stbNum);
      builder.setNum(icoNum);
      builder.setStartTime(startTime);
      builder.setEndTime(endTime);
      builder.setVoteScore(voteScore);
      builder.setDescription(ByteString.copyFrom(description.getBytes()));
      builder.setUrl(ByteString.copyFrom(url.getBytes()));

      Protocol.Transaction transaction = blockingStubFull.createAssetIssue(builder.build());
      if (transaction == null || transaction.getRawData().getContractCount() == 0) {
        logger.info("Please check!!! transaction == null");
        return false;
      }
      transaction = signTransaction(ecKey, transaction);
      GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
      if (response.getResult() == false) {
        logger.info("Please check!!! response.getresult==false");
        return false;
      } else {
        logger.info(name);
        return true;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  /**
   * constructor.
   */

  public Account queryAccount(String priKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
    byte[] address;
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
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

  private Protocol.Transaction signTransaction(ECKey ecKey, Protocol.Transaction transaction) {
    if (ecKey == null || ecKey.getPrivKey() == null) {
      logger.warn("Warning: Can't sign,there is no private key !!");
      return null;
    }
    transaction = TransactionUtils.setTimestamp(transaction);
    return TransactionUtils.sign(transaction, ecKey);
  }

  /**
   * constructor.
   */

  public boolean updateAccount(byte[] addressBytes, byte[] accountNameBytes, String priKey) {
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    AccountUpdateContract.Builder builder = AccountUpdateContract.newBuilder();
    ByteString basAddress = ByteString.copyFrom(addressBytes);
    ByteString bsAccountName = ByteString.copyFrom(accountNameBytes);

    builder.setAccountName(bsAccountName);
    builder.setOwnerAddress(basAddress);

    AccountUpdateContract contract = builder.build();
    Protocol.Transaction transaction = blockingStubFull.updateAccount(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      logger.info("Please check!!! transaction == null");
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      logger.info("Please check!!! response.getresult==false");
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return false;
    } else {
      logger.info(name);
      return true;
    }
  }

  /**
   * constructor.
   */

  public GrpcAPI.Return updateAccount2(byte[] addressBytes, byte[] accountNameBytes,
      String priKey) {
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    AccountUpdateContract.Builder builder = AccountUpdateContract.newBuilder();
    ByteString basAddreess = ByteString.copyFrom(addressBytes);
    ByteString bsAccountName = ByteString.copyFrom(accountNameBytes);

    builder.setAccountName(bsAccountName);
    builder.setOwnerAddress(basAddreess);

    AccountUpdateContract contract = builder.build();
    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull.updateAccount2(contract);

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
      logger.info("Please check!!! response.getresult==false");
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return response;
    } else {
      logger.info(name);
      return response;
    }
  }

  /**
   * constructor.
   */

  public boolean unCdBalance(byte[] address, String priKey) {
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;
    BalanceContract.UncdBalanceContract.Builder builder =
        BalanceContract.UncdBalanceContract
            .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess);

    BalanceContract.UncdBalanceContract contract = builder.build();

    Protocol.Transaction transaction = blockingStubFull.uncdBalance(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * constructor.
   */

  public GrpcAPI.Return unCdBalance2(byte[] address, String priKey) {
    //byte[] address = address;

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;
    BalanceContract.UncdBalanceContract.Builder builder =
        BalanceContract.UncdBalanceContract
            .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess);

    BalanceContract.UncdBalanceContract contract = builder.build();
    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull.uncdBalance2(contract);
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

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      return response;
    }
    return ret;
  }

  /**
   * constructor.
   */

  public Boolean voteExecutive(HashMap<String, String> executive, byte[] address, String priKey) {

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;
    ExecutiveContract.VoteExecutiveContract.Builder builder = ExecutiveContract.VoteExecutiveContract
        .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(address));
    for (String addressBase58 : executive.keySet()) {
      String value = executive.get(addressBase58);
      long count = Long.parseLong(value);
      ExecutiveContract.VoteExecutiveContract.Vote.Builder voteBuilder =
          ExecutiveContract.VoteExecutiveContract.Vote
              .newBuilder();
      byte[] addRess = WalletClient.decodeFromBase58Check(addressBase58);
      if (addRess == null) {
        return false;
      }
      voteBuilder.setVoteAddress(ByteString.copyFrom(addRess));
      voteBuilder.setVoteCount(count);
      builder.addVotes(voteBuilder.build());
    }

    ExecutiveContract.VoteExecutiveContract contract = builder.build();

    Protocol.Transaction transaction = blockingStubFull.voteExecutiveAccount(contract);
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      logger.info("transaction == null");
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);

    if (response.getResult() == false) {
      logger.info("response.getresult() == false");
      return false;
    }
    return true;
  }

  /**
   * constructor.
   */

  public GrpcAPI.Return voteExecutive2(HashMap<String, String> executive, byte[] address,
      String priKey) {

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;
    ExecutiveContract.VoteExecutiveContract.Builder builder = ExecutiveContract.VoteExecutiveContract
        .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(address));
    for (String addressBase58 : executive.keySet()) {
      String value = executive.get(addressBase58);
      long count = Long.parseLong(value);
      ExecutiveContract.VoteExecutiveContract.Vote.Builder voteBuilder =
          ExecutiveContract.VoteExecutiveContract.Vote
              .newBuilder();
      byte[] addRess = WalletClient.decodeFromBase58Check(addressBase58);
      if (addRess == null) {
        continue;
      }
      voteBuilder.setVoteAddress(ByteString.copyFrom(addRess));
      voteBuilder.setVoteCount(count);
      builder.addVotes(voteBuilder.build());
    }

    ExecutiveContract.VoteExecutiveContract contract = builder.build();

    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull
        .voteExecutiveAccount2(contract);
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
      logger.info("response.getresult() == false");
      return response;
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

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    final ECKey ecKey = temKey;

    BalanceContract.CdBalanceContract.Builder builder = BalanceContract.CdBalanceContract
        .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess).setCdedBalance(cdedBalance)
        .setCdedDuration(cdedDuration);

    BalanceContract.CdBalanceContract contract = builder.build();
    Protocol.Transaction transaction = blockingStubFull.cdBalance(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);

    return response.getResult();


  }

  /**
   * constructor.
   */

  public GrpcAPI.Return cdBalance2(byte[] addRess, long cdBalance, long cdDuration,
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
    final ECKey ecKey = temKey;

    BalanceContract.CdBalanceContract.Builder builder = BalanceContract.CdBalanceContract
        .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess).setCdedBalance(cdedBalance)
        .setCdedDuration(cdedDuration);

    BalanceContract.CdBalanceContract contract = builder.build();
    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull.cdBalance2(contract);
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

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    GrpcAPI.Return response = blockingStubFull.broadcastTransaction(transaction);

    if (response.getResult() == false) {
      return response;
    }
    return ret;
  }

  class AccountComparator implements Comparator {

    public int compare(Object o1, Object o2) {
      return Long.compare(((Account) o2).getBalance(), ((Account) o1).getBalance());
    }
  }


}



