package stest.stabila.wallet.dailybuild.zensrc20token;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.Note;
import org.stabila.api.WalletGrpc;
import org.stabila.api.WalletSolidityGrpc;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.utils.PublicMethed;
import stest.stabila.wallet.common.client.utils.ShieldedAddressInfo;
import stest.stabila.wallet.common.client.utils.ZenSrc20Base;

@Slf4j
public class ShieldSrc20Token002 extends ZenSrc20Base {

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  Optional<ShieldedAddressInfo> receiverShieldAddressInfo;
  private BigInteger publicFromAmount;
  List<Note> shieldOutList = new ArrayList<>();


  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
    publicFromAmount = getRandomAmount();
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Send shield src20 from T account to shield account")
  public void test01ShieldSrc20TransactionByTypeMint() throws Exception {
    //Query account before mint balance
    final Long beforeMintAccountBalance = getBalanceOfShieldSrc20(zenSrc20TokenOwnerAddressString,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);
    //Query contract before mint balance
    final Long beforeMintShieldAccountBalance = getBalanceOfShieldSrc20(shieldAddress,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);
    //Generate new shiled account and set note memo
    receiverShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    String memo = "Shield src20 from T account to shield account in" + System.currentTimeMillis();
    String receiverShieldAddress = receiverShieldAddressInfo.get().getAddress();

    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, receiverShieldAddress,
        "" + publicFromAmount, memo, blockingStubFull);

    //Create shiled src20 parameters
    GrpcAPI.ShieldedSRC20Parameters shieldedSrc20Parameters
        = createShieldedSrc20Parameters(publicFromAmount,
        null, null, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity
    );
    String data = encodeMintParamsToHexString(shieldedSrc20Parameters, publicFromAmount);

    //Do mint transaction type
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        mint, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);

    logger.info(mint + ":" + txid);
    logger.info(mint + infoById.get().getReceipt().getUcrUsageTotal());
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 250000);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);

    //Query account after mint balance
    Long afterMintAccountBalance = getBalanceOfShieldSrc20(zenSrc20TokenOwnerAddressString,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);
    //Query contract after mint balance
    Long afterMintShieldAccountBalance = getBalanceOfShieldSrc20(shieldAddress,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);

    Assert.assertEquals(BigInteger.valueOf(beforeMintAccountBalance - afterMintAccountBalance),
        publicFromAmount);
    Assert.assertEquals(BigInteger.valueOf(afterMintShieldAccountBalance
        - beforeMintShieldAccountBalance), publicFromAmount);

    GrpcAPI.DecryptNotesSRC20 note = scanShieldedSrc20NoteByIvk(receiverShieldAddressInfo.get(),
        blockingStubFull);
    logger.info("" + note);

    Assert.assertEquals(note.getNoteTxs(0).getNote().getValue(), publicFromAmount.longValue());
    Assert.assertEquals(note.getNoteTxs(0).getNote().getPaymentAddress(),
        receiverShieldAddressInfo.get().getAddress());
    Assert.assertEquals(note.getNoteTxs(0).getNote().getMemo(), ByteString.copyFromUtf8(memo));
    Assert.assertEquals(note.getNoteTxs(0).getTxid(), infoById.get().getId());
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


