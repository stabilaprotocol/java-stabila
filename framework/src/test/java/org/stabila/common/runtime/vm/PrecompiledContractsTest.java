package org.stabila.common.runtime.vm;

import static org.stabila.core.db.TransactionTrace.convertToStabilaAddress;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.common.application.Application;
import org.stabila.common.application.ApplicationFactory;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.runtime.ProgramResult;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.ByteUtil;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.actuator.CdBalanceActuator;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.ProposalCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.ItemNotFoundException;
import org.stabila.core.store.StoreFactory;
import org.stabila.core.vm.PrecompiledContracts;
import org.stabila.core.vm.PrecompiledContracts.PrecompiledContract;
import org.stabila.core.vm.repository.Repository;
import org.stabila.core.vm.repository.RepositoryImpl;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Proposal.State;
import org.stabila.protos.contract.BalanceContract.CdBalanceContract;

@Slf4j
public class PrecompiledContractsTest {

  // common
  private static final DataWord voteContractAddr = new DataWord(
      "0000000000000000000000000000000000000000000000000000000000010001");
  private static final DataWord withdrawBalanceAddr = new DataWord(
      "0000000000000000000000000000000000000000000000000000000000010004");
  private static final DataWord proposalApproveAddr = new DataWord(
      "0000000000000000000000000000000000000000000000000000000000010005");
  private static final DataWord proposalCreateAddr = new DataWord(
      "0000000000000000000000000000000000000000000000000000000000010006");
  private static final DataWord proposalDeleteAddr = new DataWord(
      "0000000000000000000000000000000000000000000000000000000000010007");
  private static final DataWord convertFromStabilaBytesAddressAddr = new DataWord(
      "0000000000000000000000000000000000000000000000000000000000010008");
  private static final DataWord convertFromStabilaBase58AddressAddr = new DataWord(
      "0000000000000000000000000000000000000000000000000000000000010009");
  private static final String dbPath = "output_PrecompiledContracts_test";
  private static final String ACCOUNT_NAME = "account";
  private static final String OWNER_ADDRESS;
  private static final String EXECUTIVE_NAME = "executive";
  private static final String EXECUTIVE_ADDRESS;
  private static final String EXECUTIVE_ADDRESS_BASE = "548794500882809695a8a687866e76d4271a1abc";
  private static final String URL = "https://stabila.network";
  // withdraw
  private static final long initBalance = 10_000_000_000L;
  private static final long allowance = 32_000_000L;
  private static StabilaApplicationContext context;
  private static Application appT;
  private static Manager dbManager;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    EXECUTIVE_ADDRESS = Wallet.getAddressPreFixString() + EXECUTIVE_ADDRESS_BASE;

  }


  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    // executive: executiveCapsule
    ExecutiveCapsule executiveCapsule =
        new ExecutiveCapsule(
            StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS),
            10L,
            URL);
    // executive: AccountCapsule
    AccountCapsule executiveAccountCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(EXECUTIVE_NAME),
            StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS),
            AccountType.Normal,
            initBalance);
    // some normal account
    AccountCapsule ownerAccountFirstCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME),
            StringUtil.hexString2ByteString(OWNER_ADDRESS),
            AccountType.Normal,
            10_000_000_000_000L);

    dbManager.getAccountStore()
        .put(executiveAccountCapsule.getAddress().toByteArray(), executiveAccountCapsule);
    dbManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
    dbManager.getExecutiveStore().put(executiveCapsule.getAddress().toByteArray(), executiveCapsule);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(10);
    dbManager.getDynamicPropertiesStore().saveNextMaintenanceTime(2000000);
  }

  private Any getCdContract(String ownerAddress, long cdedBalance, long duration) {
    return Any.pack(
        CdBalanceContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(ownerAddress))
            .setCdedBalance(cdedBalance)
            .setCdedDuration(duration)
            .build());
  }

  private PrecompiledContract createPrecompiledContract(DataWord addr, String ownerAddress) {
    PrecompiledContract contract = PrecompiledContracts.getContractForAddress(addr);
    contract.setCallerAddress(convertToStabilaAddress(Hex.decode(ownerAddress)));
    contract.setRepository(RepositoryImpl.createRoot(StoreFactory.getInstance()));
    ProgramResult programResult = new ProgramResult();
    contract.setResult(programResult);
    return contract;
  }

  //@Test
  public void voteExecutiveNativeTest()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
      InstantiationException, ContractValidateException, ContractExeException {
    PrecompiledContract contract = createPrecompiledContract(voteContractAddr, OWNER_ADDRESS);
    Repository deposit = RepositoryImpl.createRoot(StoreFactory.getInstance());
    byte[] executiveAddressBytes = new byte[32];
    byte[] executiveAddressBytes21 = Hex.decode(EXECUTIVE_ADDRESS);
    System.arraycopy(executiveAddressBytes21, 0, executiveAddressBytes,
        executiveAddressBytes.length - executiveAddressBytes21.length,
        executiveAddressBytes21.length);

    DataWord voteCount = new DataWord(
        "0000000000000000000000000000000000000000000000000000000000000001");
    byte[] voteCountBytes = voteCount.getData();
    byte[] data = new byte[executiveAddressBytes.length + voteCountBytes.length];
    System.arraycopy(executiveAddressBytes, 0, data, 0, executiveAddressBytes.length);
    System.arraycopy(voteCountBytes, 0, data, executiveAddressBytes.length,
        voteCountBytes.length);

    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    Any cdContract = getCdContract(OWNER_ADDRESS, cdedBalance, duration);
    Constructor<CdBalanceActuator> constructor =
        CdBalanceActuator.class
            .getDeclaredConstructor(Any.class, dbManager.getClass());
    constructor.setAccessible(true);
    CdBalanceActuator cdBalanceActuator = constructor
        .newInstance(cdContract, dbManager);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    cdBalanceActuator.validate();
    cdBalanceActuator.execute(ret);
    contract.setRepository(deposit);
    Boolean result = contract.execute(data).getLeft();
    deposit.commit();
    Assert.assertEquals(1,
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
            .get(0).getVoteCount());
    Assert.assertArrayEquals(ByteArray.fromHexString(EXECUTIVE_ADDRESS),
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
            .get(0).getVoteAddress().toByteArray());
    Assert.assertEquals(true, result);
  }

  //@Test
  public void proposalTest() {

    try {
      /*
       *  create proposal Test
       */
      DataWord key = new DataWord(
          "0000000000000000000000000000000000000000000000000000000000000000");
      // 1000000 == 0xF4240
      DataWord value = new DataWord(
          "00000000000000000000000000000000000000000000000000000000000F4240");
      byte[] data4Create = new byte[64];
      System.arraycopy(key.getData(), 0, data4Create, 0, key.getData().length);
      System
          .arraycopy(value.getData(), 0, data4Create,
              key.getData().length, value.getData().length);
      PrecompiledContract createContract = createPrecompiledContract(proposalCreateAddr,
          EXECUTIVE_ADDRESS);

      Assert.assertEquals(0, dbManager.getDynamicPropertiesStore().getLatestProposalNum());
      ProposalCapsule proposalCapsule;
      Repository deposit1 = RepositoryImpl.createRoot(StoreFactory.getInstance());
      createContract.setRepository(deposit1);
      byte[] idBytes = createContract.execute(data4Create).getRight();
      long id = ByteUtil.byteArrayToLong(idBytes);
      deposit1.commit();
      proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
      Assert.assertNotNull(proposalCapsule);
      Assert.assertEquals(1, dbManager.getDynamicPropertiesStore().getLatestProposalNum());
      Assert.assertEquals(0, proposalCapsule.getApprovals().size());
      Assert.assertEquals(1000000, proposalCapsule.getCreateTime());
      Assert.assertEquals(261200000, proposalCapsule.getExpirationTime()
      ); // 2000000 + 3 * 4 * 21600000



      /*
       *  approve proposal Test
       */

      byte[] data4Approve = new byte[64];
      DataWord isApprove = new DataWord(
          "0000000000000000000000000000000000000000000000000000000000000001");
      System.arraycopy(idBytes, 0, data4Approve, 0, idBytes.length);
      System.arraycopy(isApprove.getData(), 0, data4Approve, idBytes.length,
          isApprove.getData().length);
      PrecompiledContract approveContract = createPrecompiledContract(proposalApproveAddr,
          EXECUTIVE_ADDRESS);
      Repository deposit2 = RepositoryImpl.createRoot(StoreFactory.getInstance());
      approveContract.setRepository(deposit2);
      approveContract.execute(data4Approve);
      deposit2.commit();
      proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
      Assert.assertEquals(1, proposalCapsule.getApprovals().size());
      Assert.assertEquals(ByteString.copyFrom(ByteArray.fromHexString(EXECUTIVE_ADDRESS)),
          proposalCapsule.getApprovals().get(0));

      /*
       *  delete proposal Test
       */
      PrecompiledContract deleteContract = createPrecompiledContract(proposalDeleteAddr,
          EXECUTIVE_ADDRESS);
      Repository deposit3 = RepositoryImpl.createRoot(StoreFactory.getInstance());
      deleteContract.setRepository(deposit3);
      deleteContract.execute(idBytes);
      deposit3.commit();
      proposalCapsule = dbManager.getProposalStore().get(ByteArray.fromLong(id));
      Assert.assertEquals(State.CANCELED, proposalCapsule.getState());

    } catch (ItemNotFoundException e) {
      Assert.fail();
    }
  }

  @Test
  public void convertFromStabilaBytesAddressNativeTest() {
  }

  //@Test
  public void convertFromStabilaBase58AddressNative() {
    // 27WnTihwXsqCqpiNedWvtKCZHsLjDt4Hfmf  TestNet address
    DataWord word1 = new DataWord(
        "3237576e54696877587371437170694e65645776744b435a48734c6a44743448");
    DataWord word2 = new DataWord(
        "666d660000000000000000000000000000000000000000000000000000000000");

    byte[] data = new byte[35];
    System.arraycopy(word1.getData(), 0, data, 0, word1.getData().length);
    System.arraycopy(Arrays.copyOfRange(word2.getData(), 0, 3), 0,
        data, word1.getData().length, 3);
    PrecompiledContract contract = createPrecompiledContract(convertFromStabilaBase58AddressAddr,
        EXECUTIVE_ADDRESS);

    byte[] solidityAddress = contract.execute(data).getRight();
    Assert.assertArrayEquals(solidityAddress,
        new DataWord(Hex.decode(EXECUTIVE_ADDRESS_BASE)).getData());
  }

}
