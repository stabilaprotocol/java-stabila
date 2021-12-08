package stest.stabila.wallet.account;

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
import org.stabila.api.GrpcAPI.EmptyMessage;
import org.stabila.api.GrpcAPI.NumberMessage;
import org.stabila.api.GrpcAPI.Return;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
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
public class WalletTestAccount004 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  private final String noCdedBalanceTestKey =
      "8CB4480194192F30907E14B52498F594BD046E21D7C4D8FE866563A6760AC891";


  private final byte[] noCdedAddress = PublicMethed.getFinalAddress(noCdedBalanceTestKey);
  Long cdAmount = 2000000L;
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
  public void testCdBalance() {

    ECKey ecKey2 = new ECKey(Utils.getRandom());
    byte[] account004AddressForCd = ecKey2.getAddress();
    String account004KeyForCd = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

    Assert.assertTrue(PublicMethed.sendcoin(account004AddressForCd, 10000000,
        fromAddress, testKey002, blockingStubFull));
    //Cd failed when cd amount is large than currently balance.
    Assert.assertFalse(cdBalance(account004AddressForCd, 9000000000000000000L,
        3L, account004KeyForCd));
    //Cd failed when cd amount less than 1Stb
    Assert.assertFalse(cdBalance(account004AddressForCd, 999999L, 3L,
        account004KeyForCd));
    //Cd failed when cd duration isn't 3 days.
    //Assert.assertFalse(cdBalance(fromAddress, 1000000L, 2L, testKey002));
    //Uncd balance failed when 3 days hasn't come.
    Assert.assertFalse(PublicMethed.unCdBalance(account004AddressForCd,
        account004KeyForCd, 0, null, blockingStubFull));
    //Cd failed when cd amount is 0.
    Assert.assertFalse(cdBalance(account004AddressForCd, 0L, 3L,
        account004KeyForCd));
    //Cd failed when cd amount is -1.
    Assert.assertFalse(cdBalance(account004AddressForCd, -1L, 3L,
        account004KeyForCd));
    //Cd failed when cd duration is -1.
    //Assert.assertFalse(cdBalance(fromAddress, 1000000L, -1L, testKey002));
    //Cd failed when cd duration is 0.
    //Assert.assertFalse(cdBalance(fromAddress, 1000000L, 0L, testKey002));

  }

  @Test(enabled = true)
  public void testUnCdBalance() {
    //Uncd failed when there is no cd balance.
    //Wait to be create account

    Assert.assertFalse(PublicMethed.unCdBalance(noCdedAddress, noCdedBalanceTestKey, 1,
        null, blockingStubFull));
    logger.info("Test uncdbalance");
    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] account004Address = ecKey1.getAddress();
    String account004Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    Assert
        .assertTrue(PublicMethed.sendcoin(account004Address, cdAmount, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.cdBalance(account004Address, cdAmount, 0,
        account004Key, blockingStubFull));
    Account account004;
    account004 = PublicMethed.queryAccount(account004Address, blockingStubFull);
    Assert.assertTrue(account004.getBalance() == 0);
    Assert.assertTrue(PublicMethed.unCdBalance(account004Address, account004Key, 0,
        null, blockingStubFull));
    account004 = PublicMethed.queryAccount(account004Address, blockingStubFull);
    Assert.assertTrue(account004.getBalance() == cdAmount);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Assert.assertTrue(PublicMethed.cdBalanceGetUcr(account004Address, cdAmount, 0,
        1, account004Key, blockingStubFull));
    account004 = PublicMethed.queryAccount(account004Address, blockingStubFull);
    Assert.assertTrue(account004.getBalance() == 0);

    Assert.assertFalse(PublicMethed.unCdBalance(account004Address, account004Key, 0,
        null, blockingStubFull));
    Assert.assertTrue(PublicMethed.unCdBalance(account004Address, account004Key, 1,
        null, blockingStubFull));
    account004 = PublicMethed.queryAccount(account004Address, blockingStubFull);
    Assert.assertTrue(account004.getBalance() == cdAmount);

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
    PublicMethed.waitProduceNextBlock(searchBlockingStubFull);
    /*    while (afterBlockNum < beforeBlockNum + 1 && wait < 10) {
      Block currentBlock1 = searchBlockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
      afterBlockNum = currentBlock1.getBlockHeader().getRawData().getNumber();
      wait++;
      try {
        Thread.sleep(2000);
        logger.info("wait 2 second");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }*/

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


