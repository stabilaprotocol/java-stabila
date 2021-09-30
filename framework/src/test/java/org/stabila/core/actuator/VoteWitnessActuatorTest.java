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
import org.stabila.core.capsule.WitnessCapsule;
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
import org.stabila.protos.contract.WitnessContract.VoteWitnessContract;
import org.stabila.protos.contract.WitnessContract.VoteWitnessContract.Vote;

@Slf4j
public class VoteWitnessActuatorTest {

  private static final String dbPath = "output_VoteWitness_test";
  private static final String ACCOUNT_NAME = "account";
  private static final String OWNER_ADDRESS;
  private static final String WITNESS_NAME = "witness";
  private static final String WITNESS_ADDRESS;
  private static final String URL = "https://stabila.network";
  private static final String ADDRESS_INVALID = "aaaa";
  private static final String WITNESS_ADDRESS_NOACCOUNT;
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
    WITNESS_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    WITNESS_ADDRESS_NOACCOUNT =
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
    WitnessCapsule ownerCapsule =
        new WitnessCapsule(
            StringUtil.hexString2ByteString(WITNESS_ADDRESS),
            10L,
            URL);
    AccountCapsule witnessAccountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(WITNESS_NAME),
            StringUtil.hexString2ByteString(WITNESS_ADDRESS),
            AccountType.Normal,
            300L);
    AccountCapsule ownerAccountFirstCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(ACCOUNT_NAME),
            StringUtil.hexString2ByteString(OWNER_ADDRESS),
            AccountType.Normal,
            10_000_000_000_000L);

    dbManager.getAccountStore()
        .put(witnessAccountSecondCapsule.getAddress().toByteArray(), witnessAccountSecondCapsule);
    dbManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
    dbManager.getWitnessStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
  }

  private Any getContract(String address, String voteaddress, Long value) {
    return Any.pack(
        VoteWitnessContract.newBuilder()
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
    VoteWitnessContract.Builder builder = VoteWitnessContract.newBuilder();
    builder.setOwnerAddress(StringUtil.hexString2ByteString(address));
    for (int i = 0; i < times; i++) {
      builder.addVotes(Vote.newBuilder()
          .setVoteAddress(StringUtil.hexString2ByteString(voteaddress))
          .setVoteCount(value).build());
    }
    return Any.pack(builder.build());
  }

  /**
   * voteWitness,result is success.
   */
  @Test
  public void voteWitness() {
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(1,
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
              .get(0).getVoteCount());
      Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS),
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
              .get(0).getVoteAddress().toByteArray());
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      maintenanceManager.applyBlock(new BlockCapsule(Block.newBuilder().build()));
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10 + 1, witnessCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Invalid ownerAddress voteWitness,result is failed,exception is "Invalid address".
   */
  @Test
  public void InvalidAddress() {
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(ADDRESS_INVALID, WITNESS_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid address");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
      maintenanceManager.doMaintenance();
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use AccountStore not exists witness Address VoteWitness,result is failed,exception is "account
   * not exists".
   */
  @Test
  public void noAccount() {
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS_NOACCOUNT, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Account[" + WITNESS_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertEquals(0, dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList().size());
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + WITNESS_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      maintenanceManager.doMaintenance();
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use WitnessStore not exists Address VoteWitness,result is failed,exception is "Witness not
   * exists".
   */
  @Test
  public void noWitness() {
    AccountCapsule accountSecondCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(WITNESS_NAME),
            StringUtil.hexString2ByteString(WITNESS_ADDRESS_NOACCOUNT),
            AccountType.Normal,
            300L);
    dbManager.getAccountStore()
        .put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS_NOACCOUNT, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Witness[" + OWNER_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertEquals(0, dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList().size());
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Witness[" + WITNESS_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      maintenanceManager.doMaintenance();
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
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
            ByteString.copyFromUtf8(WITNESS_NAME),
            StringUtil.hexString2ByteString(WITNESS_ADDRESS_NOACCOUNT),
            AccountType.Normal,
            300L);
    dbManager.getAccountStore()
        .put(accountSecondCapsule.getAddress().toByteArray(), accountSecondCapsule);
    VoteWitnessActuator actuator = new VoteWitnessActuator();
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
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
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
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 0L));
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
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
    //-1 votes
    actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, -1L));
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
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * User can vote to 1 - 30 witnesses.
   */
  @Test
  public void voteCountsTest() {
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));

    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getRepeateContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L, 0));
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
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getRepeateContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L, 31));
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("VoteNumber more than maxVoteNumber 30", e.getMessage());
      maintenanceManager.doMaintenance();
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Vote 1 witness one more times.
   */
  @Test
  public void vote1WitnssOneMoreTiems() {
    long cdedBalance = 1_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getRepeateContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L, 30));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      cdBalanceActuator.validate();
      cdBalanceActuator.execute(ret);
      actuator.validate();
      actuator.execute(ret);

      maintenanceManager.doMaintenance();
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10 + 30, witnessCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use AccountStore not exists ownerAddress VoteWitness,result is failed,exception is "account not
   * exists".
   */
  @Test
  public void noOwnerAccount() {
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_NOACCOUNT, WITNESS_ADDRESS, 1L));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists", e.getMessage());
      maintenanceManager.doMaintenance();
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * witnessAccount not cd Balance, result is failed ,exception is "The total number of votes
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
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_BALANCENOTSUFFICIENT, WITNESS_ADDRESS, 1L));
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
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(10, witnessCapsule.getVoteCount());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Twice voteWitness,result is the last voteWitness.
   */
  @Test
  public void voteWitnessTwice() {
    long cdedBalance = 7_000_000_000_000L;
    long duration = 3;
    CdBalanceActuator cdBalanceActuator = new CdBalanceActuator();
    cdBalanceActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, cdedBalance, duration));
    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L));
    VoteWitnessActuator actuatorTwice = new VoteWitnessActuator();
    actuatorTwice.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 3L));
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
      Assert.assertArrayEquals(ByteArray.fromHexString(WITNESS_ADDRESS),
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS)).getVotesList()
              .get(0).getVoteAddress().toByteArray());

      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      maintenanceManager.doMaintenance();
      WitnessCapsule witnessCapsule = dbManager.getWitnessStore()
          .get(StringUtil.hexString2ByteString(WITNESS_ADDRESS).toByteArray());
      Assert.assertEquals(13, witnessCapsule.getVoteCount());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void commonErrorCheck() {

    VoteWitnessActuator actuator = new VoteWitnessActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error, expected type [VoteWitnessContract], real type[");
    actuatorTest.invalidContractType();

    actuatorTest.setContract(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L));
    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }


  @Test
  public void voteWitnessWithoutEnoughOldStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(1L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 100L));
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
  public void voteWitnessWithOldStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(2000000L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L));
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
  public void voteWitnessWithOldAndNewStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(2000000L,0L);
    owner.setCdedForStabilaPower(1000000L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 1L));
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
  public void voteWitnessWithoutEnoughOldAndNewStabilaPowerAfterNewResourceModel() {

    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(2000000L,0L);
    owner.setCdedForStabilaPower(1000000L,0L);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    VoteWitnessActuator actuator = new VoteWitnessActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, WITNESS_ADDRESS, 4000000L));
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