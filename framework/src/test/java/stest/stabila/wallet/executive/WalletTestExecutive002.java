package stest.stabila.wallet.executive;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import org.stabila.api.WalletGrpc;
import org.stabila.api.WalletSolidityGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.Protocol.Transaction;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.WalletClient;
import stest.stabila.wallet.common.client.utils.TransactionUtils;

//import stest.stabila.wallet.common.client.ExecutiveComparator;

//import stest.stabila.wallet.common.client.ExecutiveComparator;

@Slf4j
public class WalletTestExecutive002 {


  private ManagedChannel channelFull = null;
  private ManagedChannel channelSolidity = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

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
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
  }

  @Test(enabled = true)
  public void testQueryAllExecutive() {
    GrpcAPI.ExecutiveList executivelist = blockingStubFull
        .listExecutives(GrpcAPI.EmptyMessage.newBuilder().build());
    Optional<GrpcAPI.ExecutiveList> result = Optional.ofNullable(executivelist);
    if (result.isPresent()) {
      GrpcAPI.ExecutiveList executiveList = result.get();
      List<Protocol.Executive> list = executiveList.getExecutivesList();
      List<Protocol.Executive> newList = new ArrayList();
      newList.addAll(list);
      newList.sort(new ExecutiveComparator());
      GrpcAPI.ExecutiveList.Builder builder = GrpcAPI.ExecutiveList.newBuilder();
      newList.forEach(executive -> builder.addExecutives(executive));
      result = Optional.of(builder.build());
    }
    logger.info(Integer.toString(result.get().getExecutivesCount()));
    Assert.assertTrue(result.get().getExecutivesCount() > 0);
    for (int j = 0; j < result.get().getExecutivesCount(); j++) {
      Assert.assertFalse(result.get().getExecutives(j).getAddress().isEmpty());
      Assert.assertFalse(result.get().getExecutives(j).getUrl().isEmpty());
      //Assert.assertTrue(result.get().getExecutives(j).getLatestSlotNum() > 0);
      result.get().getExecutives(j).getUrlBytes();
      result.get().getExecutives(j).getLatestBlockNum();
      result.get().getExecutives(j).getLatestSlotNum();
      result.get().getExecutives(j).getTotalMissed();
      result.get().getExecutives(j).getTotalProduced();
    }

    //Improve coverage.
    executivelist.equals(result.get());
    executivelist.hashCode();
    executivelist.getSerializedSize();
    executivelist.equals(null);
  }

  @Test(enabled = true)
  public void testSolidityQueryAllExecutive() {
    GrpcAPI.ExecutiveList solidityExecutiveList = blockingStubSolidity
        .listExecutives(GrpcAPI.EmptyMessage.newBuilder().build());
    Optional<GrpcAPI.ExecutiveList> result = Optional.ofNullable(solidityExecutiveList);
    if (result.isPresent()) {
      GrpcAPI.ExecutiveList executiveList = result.get();
      List<Protocol.Executive> list = executiveList.getExecutivesList();
      List<Protocol.Executive> newList = new ArrayList();
      newList.addAll(list);
      newList.sort(new ExecutiveComparator());
      GrpcAPI.ExecutiveList.Builder builder = GrpcAPI.ExecutiveList.newBuilder();
      newList.forEach(executive -> builder.addExecutives(executive));
      result = Optional.of(builder.build());
    }
    logger.info(Integer.toString(result.get().getExecutivesCount()));
    Assert.assertTrue(result.get().getExecutivesCount() > 0);
    for (int j = 0; j < result.get().getExecutivesCount(); j++) {
      Assert.assertFalse(result.get().getExecutives(j).getAddress().isEmpty());
      Assert.assertFalse(result.get().getExecutives(j).getUrl().isEmpty());
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
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
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

  private Transaction signTransaction(ECKey ecKey, Transaction transaction) {
    if (ecKey == null || ecKey.getPrivKey() == null) {
      logger.warn("Warning: Can't sign,there is no private key !!");
      return null;
    }
    transaction = TransactionUtils.setTimestamp(transaction);
    return TransactionUtils.sign(transaction, ecKey);
  }

  class ExecutiveComparator implements Comparator {

    public int compare(Object o1, Object o2) {
      return Long
          .compare(((Protocol.Executive) o2).getVoteCount(), ((Protocol.Executive) o1).getVoteCount());
    }
  }
}


