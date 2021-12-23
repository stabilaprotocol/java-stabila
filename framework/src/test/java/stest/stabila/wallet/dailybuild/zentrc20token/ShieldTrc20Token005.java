package stest.stabila.wallet.dailybuild.zensrc20token;

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
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.utils.PublicMethed;
import stest.stabila.wallet.common.client.utils.ShieldedAddressInfo;
import stest.stabila.wallet.common.client.utils.ZenSrc20Base;

@Slf4j
public class ShieldSrc20Token005 extends ZenSrc20Base {

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  Optional<ShieldedAddressInfo> senderShieldAddressInfo;
  Optional<ShieldedAddressInfo> secondSenderShieldAddressInfo;
  private BigInteger publicFromAmount;
  List<Note> shieldOutList = new ArrayList<>();
  List<ShieldedAddressInfo> inputShieldAddressList = new ArrayList<>();
  List<GrpcAPI.DecryptNotesSRC20> inputNoteList = new ArrayList<>();
  GrpcAPI.DecryptNotesSRC20 senderNote;
  GrpcAPI.DecryptNotesSRC20 secondSenderNote;
  GrpcAPI.DecryptNotesSRC20 receiverSenderNote;
  long senderPosition;

  //get account
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] receiverAddressbyte = ecKey1.getAddress();
  String receiverKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  String receiverAddressString = PublicMethed.getAddressString(receiverKey);


  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() throws Exception {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
    publicFromAmount = getRandomAmount();

    //Generate new shiled account for sender and receiver
    senderShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    String memo = "Create a note for burn withoutask test " + System.currentTimeMillis();
    String sendShieldAddress = senderShieldAddressInfo.get().getAddress();
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, sendShieldAddress,
        "" + publicFromAmount, memo, blockingStubFull);
    //Create mint parameters
    GrpcAPI.ShieldedSRC20Parameters shieldedSrc20Parameters
        = createShieldedSrc20Parameters(publicFromAmount,
        null, null, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity);
    String data = encodeMintParamsToHexString(shieldedSrc20Parameters, publicFromAmount);
    //Do mint transaction type
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        mint, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);

    //Create second mint parameters
    memo = "Create a note for burn to one public and one shield withoutask test "
        + System.currentTimeMillis();
    secondSenderShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    String sesendShieldAddress = secondSenderShieldAddressInfo.get().getAddress();
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, sesendShieldAddress,
        "" + publicFromAmount, memo, blockingStubFull);

    shieldedSrc20Parameters
        = createShieldedSrc20Parameters(publicFromAmount,
        null, null, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity);
    data = encodeMintParamsToHexString(shieldedSrc20Parameters, publicFromAmount);
    //Do mint transaction type
    txid = PublicMethed.triggerContract(shieldAddressByte,
        mint, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);

    //Scan sender note
    senderNote = scanShieldedSrc20NoteByIvk(senderShieldAddressInfo.get(),
        blockingStubFull);
    Assert.assertEquals(senderNote.getNoteTxs(0).getIsSpent(), false);
    logger.info("" + senderNote);
    senderPosition = senderNote.getNoteTxs(0).getPosition();
    Assert.assertEquals(senderNote.getNoteTxs(0).getNote().getValue(),
        publicFromAmount.longValue());


  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Shield SRC20 transaction with type burn and without ask")
  public void test01ShieldSrc20TransactionWithTypeBurnWithoutAsk() throws Exception {
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    //Query account before mint balance
    final Long beforeBurnAccountBalance = getBalanceOfShieldSrc20(receiverAddressString,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);
    //Query contract before mint balance
    final Long beforeBurnShieldAccountBalance = getBalanceOfShieldSrc20(shieldAddress,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);

    inputShieldAddressList.add(senderShieldAddressInfo.get());
    BigInteger receiveAmount = publicFromAmount;
    //Create burn parameters
    GrpcAPI.ShieldedSRC20Parameters shieldedSrc20Parameters
        = createShieldedSrc20ParametersWithoutAsk(BigInteger.valueOf(0),
        senderNote, inputShieldAddressList, null, receiverAddressString, receiverAddressbyte,
        receiveAmount.longValue(), blockingStubFull, blockingStubSolidity);

    String data = shieldedSrc20Parameters.getTriggerContractInput();
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        burn, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 180000);

    logger.info("scanShieldedSrc20NoteByIvk + senderNote:" + senderNote);
    senderNote = scanShieldedSrc20NoteByIvk(senderShieldAddressInfo.get(),
        blockingStubFull);
    Assert.assertEquals(senderNote.getNoteTxs(0).getIsSpent(), true);

    final Long afterBurnAccountBalance = getBalanceOfShieldSrc20(receiverAddressString,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);
    //Query contract before mint balance
    final Long afterBurnShieldAccountBalance = getBalanceOfShieldSrc20(shieldAddress,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);

    logger.info("afterBurnAccountBalance       :" + afterBurnAccountBalance);
    logger.info("beforeBurnAccountBalance      :" + beforeBurnAccountBalance);
    logger.info("beforeBurnShieldAccountBalance:" + beforeBurnShieldAccountBalance);
    logger.info("afterBurnShieldAccountBalance :" + afterBurnShieldAccountBalance);
    Assert.assertEquals(BigInteger.valueOf(afterBurnAccountBalance - beforeBurnAccountBalance),
        receiveAmount);
    Assert.assertEquals(BigInteger.valueOf(beforeBurnShieldAccountBalance
            - afterBurnShieldAccountBalance),
        receiveAmount);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Shield SRC20 transaction with type burn to one "
      + "T and one Z address and without ask")
  public void test02ShieldSrc20TransactionWithTypeBurnWithoutAsk() throws Exception {
    //Scan sender note
    secondSenderNote = scanShieldedSrc20NoteByIvk(secondSenderShieldAddressInfo.get(),
        blockingStubFull);
    //Query account before mint balance
    final Long beforeBurnAccountBalance = getBalanceOfShieldSrc20(receiverAddressString,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);
    //Query contract before mint balance
    final Long beforeBurnShieldAccountBalance = getBalanceOfShieldSrc20(shieldAddress,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);

    inputShieldAddressList.clear();
    inputShieldAddressList.add(secondSenderShieldAddressInfo.get());
    BigInteger shieldReceiveAmount = BigInteger.valueOf(0);
    BigInteger receiveAmount = publicFromAmount.subtract(shieldReceiveAmount);

    ShieldedAddressInfo receiverShieldAddressInfo = getNewShieldedAddress(blockingStubFull).get();
    String receiverShieldAddress = receiverShieldAddressInfo.getAddress();
    String memo = "Burn to one shield and one public test " + System.currentTimeMillis();
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, receiverShieldAddress,
        "" + shieldReceiveAmount, memo, blockingStubFull);

    //Create burn parameters
    GrpcAPI.ShieldedSRC20Parameters shieldedSrc20Parameters
        = createShieldedSrc20ParametersWithoutAsk(BigInteger.valueOf(0),
        secondSenderNote, inputShieldAddressList, shieldOutList, receiverAddressString,
        receiverAddressbyte,
        receiveAmount.longValue(), blockingStubFull, blockingStubSolidity);

    String data = shieldedSrc20Parameters.getTriggerContractInput();
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        burn, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 180000);

    logger.info("scanShieldedSrc20NoteByIvk + senderNote:" + senderNote);
    secondSenderNote = scanShieldedSrc20NoteByIvk(secondSenderShieldAddressInfo.get(),
        blockingStubFull);
    Assert.assertEquals(secondSenderNote.getNoteTxs(0).getIsSpent(), true);

    final Long afterBurnAccountBalance = getBalanceOfShieldSrc20(receiverAddressString,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);
    //Query contract before mint balance
    final Long afterBurnShieldAccountBalance = getBalanceOfShieldSrc20(shieldAddress,
        zenSrc20TokenOwnerAddress, zenSrc20TokenOwnerKey, blockingStubFull);

    logger.info("afterBurnAccountBalance       :" + afterBurnAccountBalance);
    logger.info("beforeBurnAccountBalance      :" + beforeBurnAccountBalance);
    logger.info("beforeBurnShieldAccountBalance:" + beforeBurnShieldAccountBalance);
    logger.info("afterBurnShieldAccountBalance :" + afterBurnShieldAccountBalance);
    Assert.assertEquals(BigInteger.valueOf(afterBurnAccountBalance - beforeBurnAccountBalance),
        receiveAmount);
    Assert.assertEquals(BigInteger.valueOf(beforeBurnShieldAccountBalance
            - afterBurnShieldAccountBalance),
        receiveAmount);

    receiverSenderNote = scanShieldedSrc20NoteByIvk(receiverShieldAddressInfo,
        blockingStubFull);
    Assert.assertEquals(receiverSenderNote.getNoteTxs(0).getIsSpent(), false);
    Assert.assertEquals(receiverSenderNote.getNoteTxs(0).getNote()
        .getValue(), shieldReceiveAmount.longValue());
    Assert.assertEquals(ByteArray.toHexString(receiverSenderNote.getNoteTxs(0)
        .getTxid().toByteArray()), txid);

    secondSenderNote = scanShieldedSrc20NoteByOvk(secondSenderShieldAddressInfo.get(),
        blockingStubFull);
    logger.info(secondSenderNote.toString());
    Assert.assertEquals(secondSenderNote.getNoteTxs(0).getNote().getValue(),
        shieldReceiveAmount.longValue());


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


