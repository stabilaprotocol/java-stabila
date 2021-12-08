package org.stabila.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.consensus.ConsensusService;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.consensus.dpos.MaintenanceManager;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Block;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.AssetIssueContractOuterClass;
import org.stabila.protos.contract.BalanceContract.CdBalanceContract;
import org.stabila.protos.contract.ExecutiveContract.VoteExecutiveContract;
import org.stabila.protos.contract.ExecutiveContract.VoteExecutiveContract.Vote;

@Slf4j
public class VoteExecutiveActuatorTest {

  private static final String dbPath = "output_VoteExecutive_test";
  private static final String ACCOUNT_NAME = "account";
  private static final String OWNER_ADDRESS;
  private static final String EXECUTIVE_NAME = "executive";
  private static final String EXECUTIVE_ADDRESS;
  private static final String URL = "https://stabila.network";
  private static final String ADDRESS_INVALID = "aaaa";
  private static final String EXECUTIVE_ADDRESS_NOACCOUNT;
  private static final String OWNER_ADDRESS_NOACCOUNT;
  private static final String OWNER_ADDRESS_BALANCENOTSUFFICIENT;
  private static StabilaApplicationContext context;
  private static Manager dbManager;
  private static MaintenanceManager maintenanceManager;
  private static ConsensusService consensusService;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    EXECUTIVE_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    EXECUTIVE_ADDRESS_NOACCOUNT =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
    OWNER_ADDRESS_NOACCOUNT =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aae";
    OWNER_ADDRESS_BALANCENOTSUFFICIENT =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e06d4271a1ced";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    maintenanceManager = context.getBean(MaintenanceManager.class);
    consensusService = context.getBean(ConsensusService.class);
    consensusService.start();
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
    ExecutiveCapsule ownerCapsule =
        new ExecutiveCapsule(
            StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS),
            10L,
            URL);
    AccountCapsule executiveAccountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(EXECUTIVE_NAME),
            StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS),
            AccountType.Normal,
            300L);
    AccountCapsule ownerAccountFirstCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME),
            StringUtil.hexString2ByteString(OWNER_ADDRESS),
            AccountType.Normal,
            10_000_000_000_000L);

    dbManager.getAccountStore()
        .put(executiveAccountSecondCapsule.getAddress().toByteArray(), executiveAccountSecondCapsule);
    dbManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
    dbManager.getExecutiveStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
  }

  private Any getContract(String address, String voteaddress, Long value) {
    return Any.pack(
        VoteExecutiveContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(address))
            .addVotes(Vote.newBuilder()
                .setVoteAddress(StringUtil.hexString2ByteString(voteaddress))
                .setVoteCount(value).build())
            .build());
  }

  private Any getContract(String ownerAddress, long cdedBalance, long duration) {
    return Any.pack(
        CdBalanceContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(ownerAddress))
            .setCdedBalance(cdedBalance)
            .setCdedDuration(duration)
            .build());
  }

  private Any getRepeateContract(String address, String voteaddress, Long value, int times) {
    VoteExecutiveContract.Builder builder = VoteExecutiveContract.newBuilder();
    builder.setOwnerAddress(StringUtil.hexString2ByteString(address));
    for (int i = 0; i < times; i++) {
      builder.addVotes(Vote.newBuilder()
          .setVoteAddress(StringUtil.hexString2ByteString(voteaddress))
          .setVoteCount(value).build());
    }
    return Any.pack(builder.build());
  }

  /**
   * voteExecutive,result is success.
   */
  @Test
  public void voteExecutive() {
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(1,
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
              .get(0).getVoteCount());
      Assert.assertArrayEquals(ByteArray.fromHexString(EXECUTIVE_ADDRESS),
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
              .get(0).getVoteAddress().toByteArray());
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      maintenanceManager.applyBlock(new BlockCapsule(Block.newBuilder().build()));
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10 + 1, executiveCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Invalid ownerAddress voteExecutive,result is failed,exception is "Invalid address".
   */
  @Test
  public void InvalidAddress() {
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(ADDRESS_INVALID, EXECUTIVE_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid address");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use AccountStore not exists executive Address VoteExecutive,result is failed,exception is "account
   * not exists".
   */
  @Test
  public void noAccount() {
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS_NOACCOUNT, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Account[" + EXECUTIVE_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertEquals(0, dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList().size());
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + EXECUTIVE_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use ExecutiveStore not exists Address VoteExecutive,result is failed,exception is "Executive not
   * exists".
   */
  @Test
  public void noExecutive() {
    AccountCapsule accountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(EXECUTIVE_NAME),
            StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS_NOACCOUNT),
            AccountType.Normal,
            300L);
    dbManager.getAccountStore()
        .put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS_NOACCOUNT, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Executive[" + OWNER_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertEquals(0, dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList().size());
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Executive[" + EXECUTIVE_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * invalideVoteAddress
   */
  @Test
  public void invalideVoteAddress() {
    AccountCapsule accountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(EXECUTIVE_NAME),
            StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS_NOACCOUNT),
            AccountType.Normal,
            300L);
    dbManager.getAccountStore()
        .put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, ADDRESS_INVALID, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(0, dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList().size());
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid vote address!", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(11, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Every vote count must greater than 0.
   */
  @Test
  public void voteCountTest() {
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));
    //0 votes
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 0L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("vote count must be greater than 0", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
    //-1 votes
    actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, -1L));
    ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("vote count must be greater than 0", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * User can vote to 1 - 30 executives.
   */
  @Test
  public void voteCountsTest() {
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));

    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getRepeateContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L, 0));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("VoteNumber must more than 0", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getRepeateContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L, 31));
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("VoteNumber more than maxVoteNumber 30", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Vote 1 executive one more times.
   */
  @Test
  public void vote1WitnssOneMoreTiems() {
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getRepeateContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L, 30));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);

      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10 + 30, executiveCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use AccountStore not exists ownerAddress VoteExecutive,result is failed,exception is "account not
   * exists".
   */
  @Test
  public void noOwnerAccount() {
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_NOACCOUNT, EXECUTIVE_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * executiveAccount not cd Balance, result is failed ,exception is "The total number of votes
   * 1000000 is greater than 0.
   */
  @Test
  public void balanceNotSufficient() {
    AccountCapsule balanceNotSufficientCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("balanceNotSufficient"),
            StringUtil.hexString2ByteString(OWNER_ADDRESS_BALANCENOTSUFFICIENT),
            AccountType.Normal,
            500L);
    dbManager.getAccountStore()
        .put(balanceNotSufficientCapsule.getAddress().toByteArray(), balanceNotSufficientCapsule);
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_BALANCENOTSUFFICIENT, EXECUTIVE_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("The total number of votes[" + 1000000 + "] is greater than the stabilaPower["
          + balanceNotSufficientCapsule.getStabilaPower() + "]");
    } catch (ContractValidateException e) {
      Assert.assertEquals(0, dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS_BALANCENOTSUFFICIENT)).getVotesList().size());
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert
          .assertEquals("The total number of votes[" + 1000000 + "] is greater than the stabilaPower["
              + balanceNotSufficientCapsule.getStabilaPower() + "]", e.getMessage());
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(10, executiveCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Twice voteExecutive,result is the last voteExecutive.
   */
  @Test
  public void voteExecutiveTwice() {
    long cdedBalance = 7_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));
    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L));
    VoteExecutiveActuator actuatorTwice = new VoteExecutiveActuator();
    actuatorTwice.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 3L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      actuatorTwice.validate();
      actuatorTwice.execute(ret);
      Assert.assertEquals(3,
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
              .get(0).getVoteCount());
      Assert.assertArrayEquals(ByteArray.fromHexString(EXECUTIVE_ADDRESS),
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
              .get(0).getVoteAddress().toByteArray());

      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      maintenanceManager.doMaintenance();
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(StringUtil.hexString2ByteString(EXECUTIVE_ADDRESS).toByteArray());
      Assert.assertEquals(13, executiveCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void commonErrorCheck() {

    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error, expected type [VoteExecutiveContract], real type[");
    actuatorTest.invalidContractType();

    actuatorTest.setContract(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L));
    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }


  @Test
  public void voteExecutiveWithoutEnoughOldStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(1L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 100L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
    }
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(0L);
  }

  @Test
  public void voteExecutiveWithOldStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(2000000L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(2000000L, owner.getInstance().getOldStabilaPower());
    } catch (ContractValidateException e) {
      e.printStackTrace();
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(0L);
  }


  @Test
  public void voteExecutiveWithOldAndNewStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(2000000L,0L);
    owner.setCdedForStabilaPower(1000000L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(3000000L, owner.getAllStabilaPower());
      Assert.assertEquals(2000000L, owner.getInstance().getOldStabilaPower());
      Assert.assertEquals(1000000L, owner.getInstance().getStabilaPower().getCdedBalance());
    } catch (ContractValidateException e) {
      e.printStackTrace();
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(0L);
  }


  @Test
  public void voteExecutiveWithoutEnoughOldAndNewStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(2000000L,0L);
    owner.setCdedForStabilaPower(1000000L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteExecutiveActuator actuator = new VoteExecutiveActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, EXECUTIVE_ADDRESS, 4000000L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
    }
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(0L);
  }

}