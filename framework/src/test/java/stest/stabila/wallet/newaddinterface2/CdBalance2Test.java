package stest.stabila.wallet.newaddinterface2;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
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
import org.stabila.api.GrpcAPI.EmptyMessage;
import org.stabila.api.GrpcAPI.NumberMessage;
import org.stabila.api.GrpcAPI.Return;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.contract.BalanceContract.CdBalanceContract;
import org.stabila.protos.contract.BalanceContract.UncdBalanceContract;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;
import stest.stabila.wallet.common.client.utils.TransactionUtils;

@Slf4j
public class CdBalance2Test {

  private final String noCdedBalanceTestKey =
      "8CB4480194192F30907E14B52498F594BD046E21D7C4D8FE866563A6760AC891";

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");

  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] noCdedAddress = PublicMethed.getFinalAddress(noCdedBalanceTestKey);

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
  public void testCdBalance2() {
    //Cd failed when cd amount is large than currently balance.
    Return ret1 = cdBalance2(fromAddress, 9000000000000000000L, 3L, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : cdedBalance must be less than accountBalance");
    //Cd failed when cd amount less than 1Stb
    ret1 = cdBalance2(fromAddress, 999999L, 3L, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : cdedBalance must be more than 1STB");
    //Cd failed when cd duration isn't 3 days.
    ret1 = cdBalance2(fromAddress, 1000000L, 2L, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : cdedDuration must be less than 3 days and more than 3 days");
    //Uncd balance failed when 3 days hasn't come.
    ret1 = unCdBalance2(fromAddress, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : It's not time to uncd.");
    //Cd failed when cd amount is 0.
    ret1 = cdBalance2(fromAddress, 0L, 3L, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : cdedBalance must be positive");
    //Cd failed when cd amount is -1.
    ret1 = cdBalance2(fromAddress, -1L, 3L, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : cdedBalance must be positive");
    //Cd failed when cd duration is -1.
    ret1 = cdBalance2(fromAddress, 1000000L, -1L, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : cdedDuration must be less than 3 days and more than 3 days");
    //Cd failed when cd duration is 0.
    ret1 = cdBalance2(fromAddress, 1000000L, 0L, testKey002);
    Assert.assertEquals(ret1.getCode(), GrpcAPI.Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : cdedDuration must be less than 3 days and more than 3 days");

    try {
      Thread.sleep(16000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    //Cd balance success.
    ret1 = PublicMethed.cdBalance2(fromAddress, 1000000L, 3L, testKey002, blockingStubFull);
    Assert.assertEquals(ret1.getCode(), Return.response_code.SUCCESS);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(), "");
  }

  @Test(enabled = true)
  public void testUnCdBalance2() {
    //Uncd failed when there is no cd balance.
    Return ret1 = unCdBalance2(noCdedAddress, noCdedBalanceTestKey);
    logger.info("Test uncdbalance");
    Assert.assertEquals(ret1.getCode(), Return.response_code.CONTRACT_VALIDATE_ERROR);
    Assert.assertEquals(ret1.getMessage().toStringUtf8(),
        "contract validate error : no cdedBalance");
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
    Block currentBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
    final Long beforeBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
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

    Long afterBlockNum = 0L;
    Integer wait = 0;
    while (afterBlockNum < beforeBlockNum + 1 && wait < 10) {
      Block currentBlock1 = searchBlockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      afterBlockNum = currentBlock1.getBlockHeader().getRawData().getNumber();
      wait++;
      try {
        Thread.sleep(2000);
        logger.info("wait 2 second");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    Account afterFronzen = queryAccount(ecKey, searchBlockingStubFull);
    Long afterCdedBalance = afterFronzen.getCded(0).getCdedBalance();
    //Long afterBandwidth     = afterFronzen.getBandwidth();
    //logger.info(Long.toString(afterFronzen.getBandwidth()));
    logger.info(Long.toString(afterFronzen.getCded(0).getCdedBalance()));
    //logger.info(Integer.toString(search.getCdedCount()));
    logger.info(
        "beforefronen" + beforeCdedBalance.toString() + "    afterfronzen" + afterCdedBalance
            .toString());
    Assert.assertTrue(afterCdedBalance - beforeCdedBalance == cdBalance);
    //Assert.assertTrue(afterBandwidth - beforeBandwidth == cdBalance * cded_duration);
    return true;


  }

  /**
   * constructor.
   */

  public Return cdBalance2(byte[] addRess, long cdBalance, long cdDuration,
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
    Block currentBlock = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
    final Long beforeBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
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

    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull.cdBalance2(contract);
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

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    Return response = blockingStubFull.broadcastTransaction(transaction);

    if (response.getResult() == false) {
      return response;
    }

    Long afterBlockNum = 0L;
    Integer wait = 0;
    while (afterBlockNum < beforeBlockNum + 1 && wait < 10) {
      Block currentBlock1 = searchBlockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      afterBlockNum = currentBlock1.getBlockHeader().getRawData().getNumber();
      wait++;
      try {
        Thread.sleep(2000);
        logger.info("wait 2 second");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    Account afterFronzen = queryAccount(ecKey, searchBlockingStubFull);
    Long afterCdedBalance = afterFronzen.getCded(0).getCdedBalance();
    //Long afterBandwidth     = afterFronzen.getBandwidth();
    //logger.info(Long.toString(afterFronzen.getBandwidth()));
    logger.info(Long.toString(afterFronzen.getCded(0).getCdedBalance()));
    //logger.info(Integer.toString(search.getCdedCount()));
    logger.info(
        "beforefronen" + beforeCdedBalance.toString() + "    afterfronzen" + afterCdedBalance
            .toString());
    Assert.assertTrue(afterCdedBalance - beforeCdedBalance == cdBalance);
    //Assert.assertTrue(afterBandwidth - beforeBandwidth == cdBalance * cded_duration);
    return ret;


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
    if (response.getResult() == false) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * constructor.
   */

  public Return unCdBalance2(byte[] addRess, String priKey) {
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

    GrpcAPI.TransactionExtention transactionExtention = blockingStubFull.uncdBalance2(contract);
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

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      return response;
    }
    return ret;
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
}


