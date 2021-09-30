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
import org.stabila.core.ChainBaseManager;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.Parameter;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.AssetIssueContractOuterClass;
import org.stabila.protos.contract.BalanceContract.CdBalanceContract;
import org.stabila.protos.contract.Common.ResourceCode;

@Slf4j
public class CdBalanceActuatorTest {

  private static final String dbPath = "output_cd_balance_test";
  private static final String OWNER_ADDRESS;
  private static final String RECEIVER_ADDRESS;
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ACCOUNT_INVALID;
  private static final long initBalance = 10_000_000_000L;
  private static Manager dbManager;
  private static StabilaApplicationContext context;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    RECEIVER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
    OWNER_ACCOUNT_INVALID =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    //    Args.setParam(new String[]{"--output-directory", dbPath},
    //        "config-junit.conf");
    //    dbManager = new Manager();
    //    dbManager.init();
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
  public void createAccountCapsule() {
    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("owner"),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            AccountType.Normal,
            initBalance);
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

    AccountCapsule receiverCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("receiver"),
            ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)),
            AccountType.Normal,
            initBalance);
    dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);
  }

  private Any getContractForBandwidth(String ownerAddress, long cdedBalance, long duration) {
    return Any.pack(
        CdBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setCdedBalance(cdedBalance)
            .setCdedDuration(duration)
            .build());
  }

  private Any getContractForCpu(String ownerAddress, long cdedBalance, long duration) {
    return Any.pack(
        CdBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setCdedBalance(cdedBalance)
            .setCdedDuration(duration)
            .setResource(ResourceCode.UCR)
            .build());
  }


  private Any getContractForStabilaPower(String ownerAddress, long cdedBalance, long duration) {
    return Any.pack(
        CdBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setCdedBalance(cdedBalance)
            .setCdedDuration(duration)
            .setResource(ResourceCode.STABILA_POWER)
            .build());
  }

  private Any getDelegatedContractForBandwidth(String ownerAddress, String receiverAddress,
      long cdedBalance,
      long duration) {
    return Any.pack(
        CdBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
            .setCdedBalance(cdedBalance)
            .setCdedDuration(duration)
            .build());
  }

  private Any getDelegatedContractForCpu(String ownerAddress, String receiverAddress,
      long cdedBalance,
      long duration) {
    return Any.pack(
        CdBalanceContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
            .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
            .setCdedBalance(cdedBalance)
            .setCdedDuration(duration)
            .setResource(ResourceCode.UCR)
            .build());
  }

  @Test
  public void testCdBalanceForBandwidth() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - cdedBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(owner.getCdedBalance(), cdedBalance);
      Assert.assertEquals(cdedBalance, owner.getStabilaPower());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testCdBalanceForUcr() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForCpu(OWNER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - cdedBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getCdedBalance());
      Assert.assertEquals(cdedBalance, owner.getUcrCdedBalance());
      Assert.assertEquals(cdedBalance, owner.getStabilaPower());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testCdDelegatedBalanceForBandwidthWithContractAddress() {
    AccountCapsule receiverCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("receiver"),
            ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)),
            AccountType.Contract,
            initBalance);
    dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);

    dbManager.getDynamicPropertiesStore().saveAllowSvmConstantinople(1);

    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(), "Do not allow delegate resources to contract addresses");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testCdDelegatedBalanceForBandwidth() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long totalNetWeightBefore = dbManager.getDynamicPropertiesStore().getTotalNetWeight();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - cdedBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getCdedBalance());
      Assert.assertEquals(cdedBalance, owner.getDelegatedCdedBalanceForBandwidth());
      Assert.assertEquals(cdedBalance, owner.getStabilaPower());

      AccountCapsule receiver =
          dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(cdedBalance, receiver.getAcquiredDelegatedCdedBalanceForBandwidth());
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedCdedBalanceForUcr());
      Assert.assertEquals(0L, receiver.getStabilaPower());

      DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
          .get(DelegatedResourceCapsule
              .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
                  ByteArray.fromHexString(RECEIVER_ADDRESS)));

      Assert.assertEquals(cdedBalance, delegatedResourceCapsule.getCdedBalanceForBandwidth());
      long totalNetWeightAfter = dbManager.getDynamicPropertiesStore().getTotalNetWeight();
      Assert.assertEquals(totalNetWeightBefore + cdedBalance / 1000_000L, totalNetWeightAfter);

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(1, delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS))));

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert
          .assertEquals(1,
              delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS))));


    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testCdDelegatedBalanceForCpuSameNameTokenActive() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long totalUcrWeightBefore = dbManager.getDynamicPropertiesStore().getTotalUcrWeight();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance - cdedBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getCdedBalance());
      Assert.assertEquals(0L, owner.getDelegatedCdedBalanceForBandwidth());
      Assert.assertEquals(cdedBalance, owner.getDelegatedCdedBalanceForUcr());
      Assert.assertEquals(cdedBalance, owner.getStabilaPower());

      AccountCapsule receiver =
          dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedCdedBalanceForBandwidth());
      Assert.assertEquals(cdedBalance, receiver.getAcquiredDelegatedCdedBalanceForUcr());
      Assert.assertEquals(0L, receiver.getStabilaPower());

      DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore()
          .get(DelegatedResourceCapsule
              .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
                  ByteArray.fromHexString(RECEIVER_ADDRESS)));

      Assert.assertEquals(0L, delegatedResourceCapsule.getCdedBalanceForBandwidth());
      Assert.assertEquals(cdedBalance, delegatedResourceCapsule.getCdedBalanceForUcr());

      long totalUcrWeightAfter = dbManager.getDynamicPropertiesStore().getTotalUcrWeight();
      Assert.assertEquals(totalUcrWeightBefore + cdedBalance / 1000_000L,
          totalUcrWeightAfter);

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(1, delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS))));

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert
          .assertEquals(0, delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert
          .assertEquals(1,
              delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());
      Assert.assertEquals(true,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList()
              .contains(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS))));

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testCdDelegatedBalanceForCpuSameNameTokenClose() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(0);
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(
        getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long totalUcrWeightBefore = dbManager.getDynamicPropertiesStore().getTotalUcrWeight();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(owner.getBalance(), initBalance - cdedBalance
          - Parameter.ChainConstant.TRANSFER_FEE);
      Assert.assertEquals(0L, owner.getCdedBalance());
      Assert.assertEquals(0L, owner.getDelegatedCdedBalanceForBandwidth());
      Assert.assertEquals(0L, owner.getDelegatedCdedBalanceForUcr());
      Assert.assertEquals(0L, owner.getDelegatedCdedBalanceForUcr());

      AccountCapsule receiver =
          dbManager.getAccountStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedCdedBalanceForBandwidth());
      Assert.assertEquals(0L, receiver.getAcquiredDelegatedCdedBalanceForUcr());
      Assert.assertEquals(0L, receiver.getStabilaPower());

      long totalUcrWeightAfter = dbManager.getDynamicPropertiesStore().getTotalUcrWeight();
      Assert.assertEquals(totalUcrWeightBefore + cdedBalance / 1000_000L,
          totalUcrWeightAfter);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void cdLessThanZero() {
    long cdedBalance = -1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("cdedBalance must be positive", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void cdMoreThanBalance() {
    long cdedBalance = 11_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("cdedBalance must be less than accountBalance", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void invalidOwnerAddress() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS_INVALID, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);

      Assert.assertEquals("Invalid address", e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }

  }

  @Test
  public void invalidOwnerAccount() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ACCOUNT_INVALID, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ACCOUNT_INVALID + "] not exists",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void durationLessThanMin() {
    long cdedBalance = 1_000_000_000L;
    long duration = 2;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      long minCdedTime = dbManager.getDynamicPropertiesStore().getMinCdedTime();
      long maxCdedTime = dbManager.getDynamicPropertiesStore().getMaxCdedTime();
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("cdedDuration must be less than " + maxCdedTime + " days "
          + "and more than " + minCdedTime + " days", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void durationMoreThanMax() {
    long cdedBalance = 1_000_000_000L;
    long duration = 4;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      long minCdedTime = dbManager.getDynamicPropertiesStore().getMinCdedTime();
      long maxCdedTime = dbManager.getDynamicPropertiesStore().getMaxCdedTime();
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("cdedDuration must be less than " + maxCdedTime + " days "
          + "and more than " + minCdedTime + " days", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void lessThan1StbTest() {
    long cdedBalance = 1;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("cdedBalance must be more than 1STB", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void cdedNumTest() {
    AccountCapsule account = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    account.setCded(1_000L, 1_000_000_000L);
    account.setCded(1_000_000L, 1_000_000_000L);
    dbManager.getAccountStore().put(account.getAddress().toByteArray(), account);

    long cdedBalance = 20_000_000L;
    long duration = 3L;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("cdedCount must be 0 or 1", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  //@Test
  public void moreThanCdedNumber() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      long maxCdedNumber = Parameter.ChainConstant.MAX_CDED_NUMBER;
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("max cded number is: " + maxCdedNumber, e.getMessage());

    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void commonErrorCheck() {
    CdBalanceActuator actuator = new CdBalanceActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error,expected type [CdBalanceContract],real type[");
    actuatorTest.invalidContractType();

    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    actuatorTest.setContract(getContractForBandwidth(OWNER_ADDRESS, cdedBalance, duration));
    actuatorTest.nullTransationResult();

    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }


  @Test
  public void testCdBalanceForUcrWithoutOldStabilaPowerAfterNewResourceModel() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    chainBaseManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);
    actuator.setChainBaseManager(chainBaseManager)
        .setAny(getContractForCpu(OWNER_ADDRESS, cdedBalance, duration));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(-1L, owner.getInstance().getOldStabilaPower());
      Assert.assertEquals(0L, owner.getAllStabilaPower());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testCdBalanceForUcrWithOldStabilaPowerAfterNewResourceModel() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    chainBaseManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);
    actuator.setChainBaseManager(chainBaseManager)
        .setAny(getContractForCpu(OWNER_ADDRESS, cdedBalance, duration));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(100L,0L);
    chainBaseManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(100L, owner.getInstance().getOldStabilaPower());
      Assert.assertEquals(100L, owner.getAllStabilaPower());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testCdBalanceForStabilaPowerWithOldStabilaPowerAfterNewResourceModel() {
    long cdedBalance = 1_000_000_000L;
    long duration = 3;
    CdBalanceActuator actuator = new CdBalanceActuator();
    ChainBaseManager chainBaseManager = dbManager.getChainBaseManager();
    chainBaseManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);
    actuator.setChainBaseManager(chainBaseManager)
        .setAny(getContractForStabilaPower(OWNER_ADDRESS, cdedBalance, duration));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    AccountCapsule owner =
        dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setCdedForUcr(100L,0L);
    chainBaseManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS),owner);

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      owner =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(100L, owner.getInstance().getOldStabilaPower());
      Assert.assertEquals(cdedBalance + 100L, owner.getAllStabilaPower());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


}
