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
public class ShieldSrc20Token006 extends ZenSrc20Base {

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  private String soliInPbft = Configuration.getByPath("testng.conf")
          .getStringList("solidityNode.ip.list").get(2);
  Optional<ShieldedAddressInfo> shieldAddressInfo1;
  Optional<ShieldedAddressInfo> shieldAddressInfo2;
  String shieldAddress1;
  String shieldAddress2;
  private BigInteger publicFromAmount;
  private BigInteger shield1ReceiveAmountFor1to2;
  private BigInteger shield2ReceiveAmountFor1to2;
  private BigInteger shield1ReceiveAmountFor2to2;
  private BigInteger shield2ReceiveAmountFor2to2;
  private BigInteger shield1ReceiveAmountFor2to1;
  List<Note> shieldOutList = new ArrayList<>();
  List<ShieldedAddressInfo> inputShieldAddressList = new ArrayList<>();
  GrpcAPI.DecryptNotesSRC20 shield1Note;
  GrpcAPI.DecryptNotesSRC20 shield2Note;
  long senderPosition;

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

    channelPbft = ManagedChannelBuilder.forTarget(soliInPbft)
            .usePlaintext(true)
            .build();
    blockingStubPbft = WalletSolidityGrpc.newBlockingStub(channelPbft);

    publicFromAmount = getRandomAmount();

    //Generate new shiled account for sender and receiver
    shieldAddressInfo1 = getNewShieldedAddress(blockingStubFull);
    shieldAddressInfo2 = getNewShieldedAddress(blockingStubFull);
    String memo = "Create a note for transfer test " + System.currentTimeMillis();
    shieldAddress1 = shieldAddressInfo1.get().getAddress();
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, shieldAddress1,
        "" + publicFromAmount, memo, blockingStubFull);
    //Create mint parameters
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
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);

    //Scan sender note
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull);
    Assert.assertEquals(shield1Note.getNoteTxs(0).getIsSpent(), false);
    logger.info("" + shield1Note);
    senderPosition = shield1Note.getNoteTxs(0).getPosition();
    Assert.assertEquals(shield1Note.getNoteTxs(0).getNote().getValue(),
        publicFromAmount.longValue());


  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Shield SRC20 transfer with type 1 to 2")
  public void test01ShieldSrc20TransferWith1To2() throws Exception {
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    //Prepare parameters
    final String transferMemo1 = "1 to 2 for shieldAddressInfo1 " + System.currentTimeMillis();
    final String transferMemo2 = "1 to 2 for shieldAddressInfo2 " + System.currentTimeMillis();
    shieldAddress1 = shieldAddressInfo1.get().getAddress();
    shieldAddress2 = shieldAddressInfo2.get().getAddress();
    shield1ReceiveAmountFor1to2 = BigInteger.valueOf(30);
    shield2ReceiveAmountFor1to2 = publicFromAmount.subtract(shield1ReceiveAmountFor1to2);
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, shieldAddress1,
        "" + shield1ReceiveAmountFor1to2, transferMemo1, blockingStubFull);
    shieldOutList = addShieldSrc20OutputList(shieldOutList, shieldAddress2,
        "" + shield2ReceiveAmountFor1to2, transferMemo2, blockingStubFull);
    inputShieldAddressList.clear();
    inputShieldAddressList.add(shieldAddressInfo1.get());

    //Create transfer parameters
    GrpcAPI.ShieldedSRC20Parameters shieldedSrc20Parameters
        = createShieldedSrc20Parameters(BigInteger.valueOf(0),
        shield1Note, inputShieldAddressList, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity
    );

    //Create transfer transaction
    String data = encodeTransferParamsToHexString(shieldedSrc20Parameters);
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        transfer, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 300000);

    //Scan 1 to 2 ivk note
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull);
    shield2Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
        blockingStubFull);
    logger.info("" + shield1Note);
    logger.info("" + shield2Note);
    Assert.assertEquals(shield1Note.getNoteTxs(1).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield1Note.getNoteTxs(1).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo1));
    Assert.assertEquals(shield1Note.getNoteTxs(1).getNote().getValue(),
        shield1ReceiveAmountFor1to2.longValue());
    Assert.assertEquals(shield1Note.getNoteTxs(1).getNote().getPaymentAddress(),
        shieldAddressInfo1.get().getAddress());

    Assert.assertEquals(shield2Note.getNoteTxs(0).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield2Note.getNoteTxs(0).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo2));
    Assert.assertEquals(shield2Note.getNoteTxs(0).getNote().getValue(),
        shield2ReceiveAmountFor1to2.longValue());
    Assert.assertEquals(shield2Note.getNoteTxs(0).getNote().getPaymentAddress(),
        shieldAddressInfo2.get().getAddress());

    Assert.assertEquals(shield1Note.getNoteTxs(0).getIsSpent(), true);

    //Scan 1 to 2 ovk note
    shield1Note = scanShieldedSrc20NoteByOvk(shieldAddressInfo1.get(),
        blockingStubFull);
    logger.info("scanShieldedSrc20NoteByOvk + shield1Note:" + shield1Note);
    Assert.assertEquals(shield1Note.getNoteTxsCount(), 2);

    Assert.assertEquals(shield1Note.getNoteTxs(0).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield1Note.getNoteTxs(0).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo1));
    Assert.assertEquals(shield1Note.getNoteTxs(0).getNote().getValue(),
        shield1ReceiveAmountFor1to2.longValue());
    Assert.assertEquals(shield1Note.getNoteTxs(0).getNote().getPaymentAddress(),
        shieldAddressInfo1.get().getAddress());

    Assert.assertEquals(shield1Note.getNoteTxs(1).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield1Note.getNoteTxs(1).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo2));
    Assert.assertEquals(shield1Note.getNoteTxs(1).getNote().getValue(),
        shield2ReceiveAmountFor1to2.longValue());
    Assert.assertEquals(shield1Note.getNoteTxs(1).getNote().getPaymentAddress(),
        shieldAddressInfo2.get().getAddress());
  }


  /**
   * constructor.
   */
  @Test(enabled = true, description = "Shield SRC20 transfer with type 2 to 2")
  public void test02ShieldSrc20TransferWith2To2() throws Exception {
    //Create a new note for 2 to 2
    String memo = "Create a new note for transfer test " + System.currentTimeMillis();
    shieldAddress1 = shieldAddressInfo1.get().getAddress();
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, shieldAddress1,
        "" + publicFromAmount, memo, blockingStubFull);
    //Create mint parameters
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
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull);

    final GrpcAPI.DecryptNotesSRC20 inputNoteFor2to2 = GrpcAPI.DecryptNotesSRC20.newBuilder()
        .addNoteTxs(shield1Note.getNoteTxs(1))
        .addNoteTxs(shield1Note.getNoteTxs(2)).build();

    //Prepare parameters
    final String transferMemo1 = "2 to 2 for shieldAddressInfo1 " + System.currentTimeMillis();
    final String transferMemo2 = "2 to 2 for shieldAddressInfo2 " + System.currentTimeMillis();
    shieldAddress1 = shieldAddressInfo1.get().getAddress();
    shieldAddress2 = shieldAddressInfo2.get().getAddress();
    shield1ReceiveAmountFor2to2 = BigInteger.valueOf(5);
    shield2ReceiveAmountFor2to2 = publicFromAmount.add(shield1ReceiveAmountFor1to2)
        .subtract(shield1ReceiveAmountFor2to2);
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, shieldAddress1,
        "" + shield1ReceiveAmountFor2to2, transferMemo1, blockingStubFull);
    shieldOutList = addShieldSrc20OutputList(shieldOutList, shieldAddress2,
        "" + shield2ReceiveAmountFor2to2, transferMemo2, blockingStubFull);
    inputShieldAddressList.clear();
    inputShieldAddressList.add(shieldAddressInfo1.get());
    inputShieldAddressList.add(shieldAddressInfo1.get());

    //Create transfer parameters
    shieldedSrc20Parameters
        = createShieldedSrc20Parameters(BigInteger.valueOf(0),
        inputNoteFor2to2, inputShieldAddressList, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity
    );

    //Create transfer transaction
    data = encodeTransferParamsToHexString(shieldedSrc20Parameters);
    txid = PublicMethed.triggerContract(shieldAddressByte,
        transfer, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 300000);

    //Scan 2 to 2 ivk note
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull);
    shield2Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
        blockingStubFull);
    logger.info("" + shield1Note);
    logger.info("" + shield2Note);
    Assert.assertEquals(shield1Note.getNoteTxs(3).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield1Note.getNoteTxs(3).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo1));
    Assert.assertEquals(shield1Note.getNoteTxs(3).getNote().getValue(),
        shield1ReceiveAmountFor2to2.longValue());
    Assert.assertEquals(shield1Note.getNoteTxs(3).getNote().getPaymentAddress(),
        shieldAddressInfo1.get().getAddress());

    Assert.assertEquals(shield2Note.getNoteTxs(1).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield2Note.getNoteTxs(1).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo2));
    Assert.assertEquals(shield2Note.getNoteTxs(1).getNote().getValue(),
        shield2ReceiveAmountFor2to2.longValue());
    Assert.assertEquals(shield2Note.getNoteTxs(1).getNote().getPaymentAddress(),
        shieldAddressInfo2.get().getAddress());

    Assert.assertEquals(shield1Note.getNoteTxs(1).getIsSpent(), true);
    Assert.assertEquals(shield1Note.getNoteTxs(2).getIsSpent(), true);

    //Scan 2 to 2 ovk note
    shield1Note = scanShieldedSrc20NoteByOvk(shieldAddressInfo1.get(),
        blockingStubFull);
    logger.info("scanShieldedSrc20NoteByOvk + shield1Note:" + shield1Note);
    Assert.assertEquals(shield1Note.getNoteTxsCount(), 4);

    Assert.assertEquals(shield1Note.getNoteTxs(2).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield1Note.getNoteTxs(2).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo1));
    Assert.assertEquals(shield1Note.getNoteTxs(2).getNote().getValue(),
        shield1ReceiveAmountFor2to2.longValue());
    Assert.assertEquals(shield1Note.getNoteTxs(2).getNote().getPaymentAddress(),
        shieldAddressInfo1.get().getAddress());

    Assert.assertEquals(shield1Note.getNoteTxs(3).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield1Note.getNoteTxs(3).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo2));
    Assert.assertEquals(shield1Note.getNoteTxs(3).getNote().getValue(),
        shield2ReceiveAmountFor2to2.longValue());
    Assert.assertEquals(shield1Note.getNoteTxs(3).getNote().getPaymentAddress(),
        shieldAddressInfo2.get().getAddress());
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Shield SRC20 transfer with type 2 to 1")
  public void test03ShieldSrc20TransferWith2To1() throws Exception {
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    shield2Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
        blockingStubFull);

    //Prepare parameters
    final String transferMemo1 = "2 to 1 for shieldAddressInfo1 " + System.currentTimeMillis();

    shieldAddress1 = shieldAddressInfo1.get().getAddress();
    shield1ReceiveAmountFor2to1 = BigInteger.valueOf(shield2Note.getNoteTxs(0)
        .getNote().getValue() + shield2Note.getNoteTxs(1).getNote().getValue());
    shieldOutList.clear();
    shieldOutList = addShieldSrc20OutputList(shieldOutList, shieldAddress1,
        "" + shield1ReceiveAmountFor2to1, transferMemo1, blockingStubFull);
    inputShieldAddressList.clear();
    inputShieldAddressList.add(shieldAddressInfo2.get());
    inputShieldAddressList.add(shieldAddressInfo2.get());

    //Create transfer parameters
    GrpcAPI.ShieldedSRC20Parameters shieldedSrc20Parameters
        = createShieldedSrc20Parameters(BigInteger.valueOf(0),
        shield2Note, inputShieldAddressList, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity
    );

    //Create transfer transaction
    String data = encodeTransferParamsToHexString(shieldedSrc20Parameters);
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        transfer, data, true, 0, maxFeeLimit, zenSrc20TokenOwnerAddress,
        zenSrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    Assert.assertTrue(infoById.get().getReceipt().getUcrUsageTotal() > 300000);

    //Scan 2 to 1 ivk note
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull);
    shield2Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
        blockingStubFull);
    logger.info("" + shield1Note);
    logger.info("" + shield2Note);
    Assert.assertEquals(shield1Note.getNoteTxs(4).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield1Note.getNoteTxs(4).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo1));
    Assert.assertEquals(shield1Note.getNoteTxs(4).getNote().getValue(),
        shield1ReceiveAmountFor2to1.longValue());
    Assert.assertEquals(shield1Note.getNoteTxs(4).getNote().getPaymentAddress(),
        shieldAddressInfo1.get().getAddress());

    Assert.assertEquals(shield2Note.getNoteTxs(0).getIsSpent(), true);
    Assert.assertEquals(shield2Note.getNoteTxs(1).getIsSpent(), true);

    //Scan 2 to 1 ovk note
    shield2Note = scanShieldedSrc20NoteByOvk(shieldAddressInfo2.get(),
        blockingStubFull);
    logger.info("scanShieldedSrc20NoteByOvk + shield1Note:" + shield2Note);
    Assert.assertEquals(shield2Note.getNoteTxsCount(), 1);

    Assert.assertEquals(shield2Note.getNoteTxs(0).getTxid(), infoById.get().getId());
    Assert.assertEquals(shield2Note.getNoteTxs(0).getNote().getValue(),
        shield1ReceiveAmountFor2to1.longValue());
    Assert.assertEquals(shield2Note.getNoteTxs(0).getNote().getPaymentAddress(),
        shieldAddressInfo1.get().getAddress());

  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Scan shield src20 note by ivk and ovk on solidity")
  public void test04ScanShieldSrc20NoteByIvkAndOvkOnSolidity() throws Exception {
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull);
    GrpcAPI.DecryptNotesSRC20 shield1NoteOnSolidity
        = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull, blockingStubSolidity);
    Assert.assertEquals(shield1Note, shield1NoteOnSolidity);

    shield2Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
        blockingStubFull);
    GrpcAPI.DecryptNotesSRC20 shield2NoteOnSolidity
        = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
        blockingStubFull, blockingStubSolidity);
    Assert.assertEquals(shield2Note, shield2NoteOnSolidity);

    shield1Note = scanShieldedSrc20NoteByOvk(shieldAddressInfo1.get(),
        blockingStubFull);
    shield1NoteOnSolidity
        = scanShieldedSrc20NoteByOvk(shieldAddressInfo1.get(),
        blockingStubFull, blockingStubSolidity);
    Assert.assertEquals(shield1Note, shield1NoteOnSolidity);

    shield2Note = scanShieldedSrc20NoteByOvk(shieldAddressInfo2.get(),
        blockingStubFull);
    shield2NoteOnSolidity
        = scanShieldedSrc20NoteByOvk(shieldAddressInfo2.get(),
        blockingStubFull, blockingStubSolidity);
    Assert.assertEquals(shield2Note, shield2NoteOnSolidity);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Scan shield src20 note by ivk and ovk on pbft")
  public void test04ScanShieldSrc20NoteByIvkAndOvkOnPbft() throws Exception {
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
            blockingStubFull);
    GrpcAPI.DecryptNotesSRC20 shield1NoteOnPbft
            = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
            blockingStubFull, blockingStubPbft);
    Assert.assertEquals(shield1Note, shield1NoteOnPbft);

    shield2Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
            blockingStubFull);
    GrpcAPI.DecryptNotesSRC20 shield2NoteOnPbft
            = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
            blockingStubFull, blockingStubPbft);
    Assert.assertEquals(shield2Note, shield2NoteOnPbft);

    shield1Note = scanShieldedSrc20NoteByOvk(shieldAddressInfo1.get(),
            blockingStubFull);
    shield1NoteOnPbft
            = scanShieldedSrc20NoteByOvk(shieldAddressInfo1.get(),
            blockingStubFull, blockingStubPbft);
    Assert.assertEquals(shield1Note, shield1NoteOnPbft);

    shield2Note = scanShieldedSrc20NoteByOvk(shieldAddressInfo2.get(),
            blockingStubFull);
    shield2NoteOnPbft
            = scanShieldedSrc20NoteByOvk(shieldAddressInfo2.get(),
            blockingStubFull, blockingStubPbft);
    Assert.assertEquals(shield2Note, shield2NoteOnPbft);
  }


  /**
   * constructor.
   */
  @Test(enabled = true, description = "Query is shield src20 note spend on solidity and pbft")
  public void test05IsShieldSrc20NoteSpendOnSolidityAndPbft() throws Exception {
    shield1Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo1.get(),
        blockingStubFull);
    shield2Note = scanShieldedSrc20NoteByIvk(shieldAddressInfo2.get(),
        blockingStubFull);

    Assert.assertEquals(getSrc20SpendResult(shieldAddressInfo1.get(),
        shield1Note.getNoteTxs(0), blockingStubFull), true);

    Assert.assertEquals(getSrc20SpendResult(shieldAddressInfo1.get(),
        shield1Note.getNoteTxs(0), blockingStubFull),
        getSrc20SpendResult(shieldAddressInfo1.get(),
            shield1Note.getNoteTxs(0), blockingStubFull, blockingStubSolidity));

    Assert.assertTrue(getSrc20SpendResult(shieldAddressInfo1.get(),
            shield1Note.getNoteTxs(0), blockingStubFull, blockingStubPbft));

    boolean spend = getSrc20SpendResult(shieldAddressInfo1.get(),shield1Note.getNoteTxs(1),
        blockingStubFull);

    Assert.assertEquals(spend,
        getSrc20SpendResult(shieldAddressInfo1.get(), shield1Note.getNoteTxs(1),
            blockingStubFull, blockingStubSolidity));
    Assert.assertEquals(spend,
        getSrc20SpendResult(shieldAddressInfo1.get(), shield1Note.getNoteTxs(1),
            blockingStubFull, blockingStubPbft));

    spend = getSrc20SpendResult(shieldAddressInfo2.get(),shield2Note.getNoteTxs(0),
        blockingStubFull);
    Assert.assertEquals(spend,
        getSrc20SpendResult(shieldAddressInfo2.get(), shield2Note.getNoteTxs(0),
            blockingStubFull, blockingStubSolidity));
    Assert.assertEquals(spend,
        getSrc20SpendResult(shieldAddressInfo2.get(), shield2Note.getNoteTxs(0),
            blockingStubFull, blockingStubPbft));

    spend = getSrc20SpendResult(shieldAddressInfo2.get(),shield2Note.getNoteTxs(1),
        blockingStubFull);
    Assert.assertEquals(spend,
        getSrc20SpendResult(shieldAddressInfo2.get(), shield2Note.getNoteTxs(1),
            blockingStubFull, blockingStubSolidity));
    Assert.assertEquals(spend,
        getSrc20SpendResult(shieldAddressInfo2.get(), shield2Note.getNoteTxs(1),
            blockingStubFull, blockingStubPbft));

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


