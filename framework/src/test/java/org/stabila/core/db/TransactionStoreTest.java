package org.stabila.core.db;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.Random;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.core.ChainBaseManager;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.exception.BadItemException;
import org.stabila.core.exception.ItemNotFoundException;
import org.stabila.common.application.Application;
import org.stabila.common.application.ApplicationFactory;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.contract.AccountContract.AccountCreateContract;
import org.stabila.protos.contract.BalanceContract.TransferContract;
import org.stabila.protos.contract.ExecutiveContract.VoteExecutiveContract;
import org.stabila.protos.contract.ExecutiveContract.VoteExecutiveContract.Vote;
import org.stabila.protos.contract.ExecutiveContract.ExecutiveCreateContract;

public class TransactionStoreTest {

  private static final byte[] key1 = TransactionStoreTest.randomBytes(21);
  private static final byte[] key2 = TransactionStoreTest.randomBytes(21);
  private static final String URL = "https://stabila.network";
  private static final String ACCOUNT_NAME = "ownerF";
  private static final String OWNER_ADDRESS =
      Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
  private static final String TO_ADDRESS =
      Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
  private static final long AMOUNT = 100;
  private static final String EXECUTIVE_ADDRESS =
      Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
  private static String dbPath = "output_TransactionStore_test";
  private static String dbDirectory = "db_TransactionStore_test";
  private static String indexDirectory = "index_TransactionStore_test";
  private static TransactionStore transactionStore;
  private static StabilaApplicationContext context;
  private static Application AppT;
  private static ChainBaseManager chainBaseManager;

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    Args.setParam(new String[]{"--output-directory", dbPath, "--storage-db-directory",
        dbDirectory, "--storage-index-directory", indexDirectory, "-w"}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
    chainBaseManager = context.getBean(ChainBaseManager.class);
    transactionStore = chainBaseManager.getTransactionStore();
  }

  /**
   * release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  /**
   * genarate random bytes.
   */
  public static byte[] randomBytes(int length) {
    // generate the random number
    byte[] result = new byte[length];
    new Random().nextBytes(result);
    result[0] = Wallet.getAddressPreFixByte();
    return result;
  }

  /**
   * get AccountCreateContract.
   */
  private AccountCreateContract getContract(String name, String address) {
    return AccountCreateContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
        .build();
  }

  /**
   * get TransferContract.
   */
  private TransferContract getContract(long count, String owneraddress, String toaddress) {
    return TransferContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(owneraddress)))
        .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(toaddress)))
        .setAmount(count)
        .build();
  }

  /**
   * get ExecutiveCreateContract.
   */
  private ExecutiveCreateContract getExecutiveContract(String address, String url) {
    return ExecutiveCreateContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
        .setUrl(ByteString.copyFrom(ByteArray.fromString(url)))
        .build();
  }

  /**
   * get VoteExecutiveContract.
   */
  private VoteExecutiveContract getVoteExecutiveContract(String address, String voteaddress,
      Long value) {
    return
        VoteExecutiveContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
            .addVotes(Vote.newBuilder()
                .setVoteAddress(ByteString.copyFrom(ByteArray.fromHexString(voteaddress)))
                .setVoteCount(value).build())
            .build();
  }

  @Test
  public void getTransactionTest() throws BadItemException, ItemNotFoundException {
    final BlockStore blockStore = chainBaseManager.getBlockStore();
    final TransactionStore stbStore = chainBaseManager.getTransactionStore();
    String key = "f31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62";

    BlockCapsule blockCapsule =
        new BlockCapsule(
            1,
            Sha256Hash.wrap(chainBaseManager.getGenesisBlockId().getByteString()),
            1,
            ByteString.copyFrom(
                ECKey.fromPrivate(
                    ByteArray.fromHexString(key)).getAddress()));

    // save in database with block number
    TransferContract tc =
        TransferContract.newBuilder()
            .setAmount(10)
            .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
            .setToAddress(ByteString.copyFromUtf8("bbb"))
            .build();
    TransactionCapsule stb = new TransactionCapsule(tc, ContractType.TransferContract);
    blockCapsule.addTransaction(stb);
    stb.setBlockNum(blockCapsule.getNum());
    blockStore.put(blockCapsule.getBlockId().getBytes(), blockCapsule);
    stbStore.put(stb.getTransactionId().getBytes(), stb);
    Assert.assertEquals("Get transaction is error",
        stbStore.get(stb.getTransactionId().getBytes()).getInstance(), stb.getInstance());

    // no found in transaction store database
    tc =
        TransferContract.newBuilder()
            .setAmount(1000)
            .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
            .setToAddress(ByteString.copyFromUtf8("bbb"))
            .build();
    stb = new TransactionCapsule(tc, ContractType.TransferContract);
    Assert.assertNull(stbStore.get(stb.getTransactionId().getBytes()));

    // no block number, directly save in database
    tc =
        TransferContract.newBuilder()
            .setAmount(10000)
            .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
            .setToAddress(ByteString.copyFromUtf8("bbb"))
            .build();
    stb = new TransactionCapsule(tc, ContractType.TransferContract);
    stbStore.put(stb.getTransactionId().getBytes(), stb);
    Assert.assertEquals("Get transaction is error",
        stbStore.get(stb.getTransactionId().getBytes()).getInstance(), stb.getInstance());
  }

  /**
   * put and get CreateAccountTransaction.
   */
  @Test
  public void createAccountTransactionStoreTest() throws BadItemException {
    AccountCreateContract accountCreateContract = getContract(ACCOUNT_NAME,
        OWNER_ADDRESS);
    TransactionCapsule ret = new TransactionCapsule(accountCreateContract,
        chainBaseManager.getAccountStore());
    transactionStore.put(key1, ret);
    Assert.assertEquals("Store CreateAccountTransaction is error",
        transactionStore.get(key1).getInstance(),
        ret.getInstance());
    Assert.assertTrue(transactionStore.has(key1));
  }

  @Test
  public void getUncheckedTransactionTest() {
    final BlockStore blockStore = chainBaseManager.getBlockStore();
    final TransactionStore stbStore = chainBaseManager.getTransactionStore();
    String key = "f31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62";

    BlockCapsule blockCapsule =
        new BlockCapsule(
            1,
            Sha256Hash.wrap(chainBaseManager.getGenesisBlockId().getByteString()),
            1,
            ByteString.copyFrom(
                ECKey.fromPrivate(
                    ByteArray.fromHexString(key)).getAddress()));

    // save in database with block number
    TransferContract tc =
        TransferContract.newBuilder()
            .setAmount(10)
            .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
            .setToAddress(ByteString.copyFromUtf8("bbb"))
            .build();
    TransactionCapsule stb = new TransactionCapsule(tc, ContractType.TransferContract);
    blockCapsule.addTransaction(stb);
    stb.setBlockNum(blockCapsule.getNum());
    blockStore.put(blockCapsule.getBlockId().getBytes(), blockCapsule);
    stbStore.put(stb.getTransactionId().getBytes(), stb);
    Assert.assertEquals("Get transaction is error",
        stbStore.getUnchecked(stb.getTransactionId().getBytes()).getInstance(), stb.getInstance());

    // no found in transaction store database
    tc =
        TransferContract.newBuilder()
            .setAmount(1000)
            .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
            .setToAddress(ByteString.copyFromUtf8("bbb"))
            .build();
    stb = new TransactionCapsule(tc, ContractType.TransferContract);
    Assert.assertNull(stbStore.getUnchecked(stb.getTransactionId().getBytes()));

    // no block number, directly save in database
    tc =
        TransferContract.newBuilder()
            .setAmount(10000)
            .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
            .setToAddress(ByteString.copyFromUtf8("bbb"))
            .build();
    stb = new TransactionCapsule(tc, ContractType.TransferContract);
    stbStore.put(stb.getTransactionId().getBytes(), stb);
    Assert.assertEquals("Get transaction is error",
        stbStore.getUnchecked(stb.getTransactionId().getBytes()).getInstance(), stb.getInstance());
  }

  /**
   * put and get CreateExecutiveTransaction.
   */
  @Test
  public void createExecutiveTransactionStoreTest() throws BadItemException {
    ExecutiveCreateContract executiveContract = getExecutiveContract(OWNER_ADDRESS, URL);
    TransactionCapsule transactionCapsule = new TransactionCapsule(executiveContract);
    transactionStore.put(key1, transactionCapsule);
    Assert.assertEquals("Store CreateExecutiveTransaction is error",
        transactionStore.get(key1).getInstance(),
        transactionCapsule.getInstance());
  }

  /**
   * put and get TransferTransaction.
   */
  @Test
  public void transferTransactionStorenTest() throws BadItemException {
    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            AccountType.AssetIssue,
            1000000L
        );
    chainBaseManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
    TransferContract transferContract = getContract(AMOUNT, OWNER_ADDRESS, TO_ADDRESS);
    TransactionCapsule transactionCapsule = new TransactionCapsule(transferContract,
        chainBaseManager.getAccountStore());
    transactionStore.put(key1, transactionCapsule);
    Assert.assertEquals("Store TransferTransaction is error",
        transactionStore.get(key1).getInstance(),
        transactionCapsule.getInstance());
  }

  /**
   * put and get VoteExecutiveTransaction.
   */

  @Test
  public void voteExecutiveTransactionTest() throws BadItemException {

    AccountCapsule ownerAccountFirstCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            AccountType.Normal,
            1_000_000_000_000L);
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    ownerAccountFirstCapsule.setCded(cdedBalance, duration);
    chainBaseManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
    VoteExecutiveContract actuator = getVoteExecutiveContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L);
    TransactionCapsule transactionCapsule = new TransactionCapsule(actuator);
    transactionStore.put(key1, transactionCapsule);
    Assert.assertEquals("Store VoteExecutiveTransaction is error",
        transactionStore.get(key1).getInstance(),
        transactionCapsule.getInstance());
  }

  /**
   * put value is null and get it.
   */
  @Test
  public void transactionValueNullTest() throws BadItemException {
    TransactionCapsule transactionCapsule = null;
    transactionStore.put(key2, transactionCapsule);
    Assert.assertNull("put value is null", transactionStore.get(key2));

  }

  /**
   * put key is null and get it.
   */
  @Test
  public void transactionKeyNullTest() throws BadItemException {
    AccountCreateContract accountCreateContract = getContract(ACCOUNT_NAME,
        OWNER_ADDRESS);
    TransactionCapsule ret = new TransactionCapsule(accountCreateContract,
        chainBaseManager.getAccountStore());
    byte[] key = null;
    transactionStore.put(key, ret);
    try {
      transactionStore.get(key);
    } catch (RuntimeException e) {
      Assert.assertEquals("The key argument cannot be null", e.getMessage());
    }
  }
}
