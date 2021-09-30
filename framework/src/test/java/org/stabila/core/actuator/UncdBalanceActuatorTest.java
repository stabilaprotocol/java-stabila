package org.stabila.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.VotesCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.Protocol.Vote;
import org.stabila.protos.contract.AssetIssueContractOuterClass;
import org.stabila.protos.contract.BalanceContract.UncdBalanceContract;
import org.stabila.protos.contract.Common.ResourceCode;

@Slf4j
public class UncdBalanceActuatorTest {

  private static final String dbPath = "output_uncd_balance_test";
  private static final String OWNER_ADDRESS;
  private static final String RECEIVER_ADDRESS;
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ACCOUNT_INVALID;
  private static final long initBalance = 10_000_000_000L;
  private static final long cdedBalance = 1_000_000_000L;
  private static final long smallTatalResource = 100L;
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
    AccountCapsule ownerCapsule = new AccountCapsule(ByteString.copyFromUtf8("owner"),
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), AccountType.Normal,
        initBalance);
    dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);

    AccountCapsule receiverCapsule = new AccountCapsule(ByteString.copyFromUtf8("receiver"),
        ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)), AccountType.Normal,
        initBalance);
    dbManager.getAccountStore().put(receiverCapsule.getAddress().toByteArray(), receiverCapsule);
  }

  private Any getContractForBandwidth(String ownerAddress) {
    return Any.pack(UncdBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress))).build());
  }

  private Any getContractForCpu(String ownerAddress) {
    return Any.pack(UncdBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setResource(ResourceCode.UCR).build());
  }

  private Any getContractForStabilaPower(String ownerAddress) {
    return Any.pack(UncdBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setResource(ResourceCode.STABILA_POWER).build());
  }

  private Any getDelegatedContractForBandwidth(String ownerAddress, String receiverAddress) {
    return Any.pack(UncdBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress))).build());
  }

  private Any getDelegatedContractForCpu(String ownerAddress, String receiverAddress) {
    return Any.pack(UncdBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setReceiverAddress(ByteString.copyFrom(ByteArray.fromHexString(receiverAddress)))
        .setResource(ResourceCode.UCR).build());
  }

  private Any getContract(String ownerAddress, ResourceCode resourceCode) {
    return Any.pack(UncdBalanceContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setResource(resourceCode).build());
  }


  @Test
  public void testUncdBalanceForBandwidth() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCded(cdedBalance, now);
    Assert.assertEquals(accountCapsule.getCdedBalance(), cdedBalance);
    Assert.assertEquals(accountCapsule.getStabilaPower(), cdedBalance);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    long totalNetWeightBefore = dbManager.getDynamicPropertiesStore().getTotalNetWeight();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance + cdedBalance);
      Assert.assertEquals(owner.getCdedBalance(), 0);
      Assert.assertEquals(owner.getStabilaPower(), 0L);

      long totalNetWeightAfter = dbManager.getDynamicPropertiesStore().getTotalNetWeight();
      Assert.assertEquals(totalNetWeightBefore,
          totalNetWeightAfter + cdedBalance / 1000_000L);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testUncdBalanceForUcr() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCdedForUcr(cdedBalance, now);
    Assert.assertEquals(accountCapsule.getAllCdedBalanceForUcr(), cdedBalance);
    Assert.assertEquals(accountCapsule.getStabilaPower(), cdedBalance);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForCpu(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    long totalUcrWeightBefore = dbManager.getDynamicPropertiesStore().getTotalUcrWeight();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getBalance(), initBalance + cdedBalance);
      Assert.assertEquals(owner.getUcrCdedBalance(), 0);
      Assert.assertEquals(owner.getStabilaPower(), 0L);
      long totalUcrWeightAfter = dbManager.getDynamicPropertiesStore().getTotalUcrWeight();
      Assert.assertEquals(totalUcrWeightBefore,
          totalUcrWeightAfter + cdedBalance / 1000_000L);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUncdDelegatedBalanceForBandwidth() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(cdedBalance, owner.getStabilaPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(0L, receiver.getStabilaPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setCdedBalanceForBandwidth(cdedBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      AccountCapsule receiverResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(RECEIVER_ADDRESS));

      Assert.assertEquals(initBalance + cdedBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getStabilaPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedCdedBalanceForBandwidth());
      Assert.assertEquals(0L, receiverResult.getAllCdedBalanceForBandwidth());

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUncdDelegatedBalanceForBandwidthWithDeletedReceiver() {

    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(cdedBalance, owner.getStabilaPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(0L, receiver.getStabilaPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setCdedBalanceForBandwidth(cdedBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getDynamicPropertiesStore().saveAllowSvmConstantinople(0);
    dbManager.getAccountStore().delete(receiver.createDbKey());
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "Receiver Account[a0abd4b9367799eaa3197fecb144eb71de1e049150] does not exist");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowSvmConstantinople(1);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + cdedBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getStabilaPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedCdedBalanceForBandwidth());

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert
          .assertEquals(0,
              delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());

    } catch (ContractValidateException e) {
      logger.error("", e);
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  @Test
  public void testUncdDelegatedBalanceForBandwidthWithRecreatedReceiver() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    dbManager.getDynamicPropertiesStore().saveAllowSvmConstantinople(1);

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(cdedBalance, owner.getStabilaPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(0L, receiver.getStabilaPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(),
        receiver.getAddress()
    );
    delegatedResourceCapsule.setCdedBalanceForBandwidth(
        cdedBalance,
        now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getAccountStore().delete(receiver.createDbKey());
    receiver = new AccountCapsule(receiver.getAddress(), ByteString.EMPTY, AccountType.Normal);
    receiver.setAcquiredDelegatedCdedBalanceForBandwidth(10L);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);
    receiver = dbManager.getAccountStore().get(receiver.createDbKey());
    Assert.assertEquals(10, receiver.getAcquiredDelegatedCdedBalanceForBandwidth());

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(0);
    dbManager.getDynamicPropertiesStore().saveAllowSvmSolidity059(0);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "AcquiredDelegatedCdedBalanceForBandwidth[10] < delegatedBandwidth[1000000000]");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(1);
    dbManager.getDynamicPropertiesStore().saveAllowSvmSolidity059(1);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + cdedBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getStabilaPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedCdedBalanceForBandwidth());

      //check DelegatedResourceAccountIndex
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleOwner = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getFromAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleOwner.getToAccountsList().size());

      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsuleReceiver = dbManager
          .getDelegatedResourceAccountIndexStore().get(ByteArray.fromHexString(RECEIVER_ADDRESS));
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getToAccountsList().size());
      Assert.assertEquals(0,
          delegatedResourceAccountIndexCapsuleReceiver.getFromAccountsList().size());

      receiver = dbManager.getAccountStore().get(receiver.createDbKey());
      Assert.assertEquals(0, receiver.getAcquiredDelegatedCdedBalanceForBandwidth());

    } catch (ContractValidateException e) {
      logger.error("", e);
      Assert.fail();
    } catch (ContractExeException e) {
      Assert.fail();
    }
  }

  /**
   * when SameTokenName close,delegate balance cded, unfreoze show error
   */
  @Test
  public void testUncdDelegatedBalanceForBandwidthSameTokenNameClose() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(0);

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(cdedBalance, owner.getStabilaPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.setAcquiredDelegatedCdedBalanceForBandwidth(cdedBalance);
    Assert.assertEquals(0L, receiver.getStabilaPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

    //init DelegatedResourceCapsule
    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setCdedBalanceForBandwidth(cdedBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    //init DelegatedResourceAccountIndex
    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              owner.getAddress());
      delegatedResourceAccountIndex
          .addToAccount(ByteString.copyFrom(ByteArray.fromHexString(RECEIVER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(OWNER_ADDRESS), delegatedResourceAccountIndex);
    }

    {
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndex =
          new DelegatedResourceAccountIndexCapsule(
              receiver.getAddress());
      delegatedResourceAccountIndex
          .addFromAccount(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
      dbManager.getDelegatedResourceAccountIndexStore()
          .put(ByteArray.fromHexString(RECEIVER_ADDRESS), delegatedResourceAccountIndex);
    }

    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForBandwidth(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("no cdedBalance(BANDWIDTH)", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUncdDelegatedBalanceForCpu() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.addDelegatedCdedBalanceForUcr(cdedBalance);
    Assert.assertEquals(cdedBalance, owner.getStabilaPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.addAcquiredDelegatedCdedBalanceForUcr(cdedBalance);
    Assert.assertEquals(0L, receiver.getStabilaPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);

    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setCdedBalanceForUcr(cdedBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      AccountCapsule receiverResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(RECEIVER_ADDRESS));

      Assert.assertEquals(initBalance + cdedBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getStabilaPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedCdedBalanceForUcr());
      Assert.assertEquals(0L, receiverResult.getAllCdedBalanceForUcr());
    } catch (ContractValidateException e) {
      logger.error("", e);
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUncdDelegatedBalanceForCpuWithDeletedReceiver() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.addDelegatedCdedBalanceForUcr(cdedBalance);
    Assert.assertEquals(cdedBalance, owner.getStabilaPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.addAcquiredDelegatedCdedBalanceForUcr(cdedBalance);
    Assert.assertEquals(0L, receiver.getStabilaPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(), receiver.getAddress());
    delegatedResourceCapsule.setCdedBalanceForUcr(cdedBalance, now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getDynamicPropertiesStore().saveAllowSvmConstantinople(0);
    dbManager.getAccountStore().delete(receiver.createDbKey());

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "Receiver Account[a0abd4b9367799eaa3197fecb144eb71de1e049150] does not exist");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowSvmConstantinople(1);

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + cdedBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getStabilaPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedCdedBalanceForUcr());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testUncdDelegatedBalanceForCpuWithRecreatedReceiver() {
    dbManager.getDynamicPropertiesStore().saveAllowDelegateResource(1);
    dbManager.getDynamicPropertiesStore().saveAllowSvmConstantinople(1);

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule owner = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.addDelegatedCdedBalanceForUcr(cdedBalance);
    Assert.assertEquals(cdedBalance, owner.getStabilaPower());

    AccountCapsule receiver = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(RECEIVER_ADDRESS));
    receiver.addAcquiredDelegatedCdedBalanceForUcr(cdedBalance);
    Assert.assertEquals(0L, receiver.getStabilaPower());

    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    DelegatedResourceCapsule delegatedResourceCapsule = new DelegatedResourceCapsule(
        owner.getAddress(),
        receiver.getAddress()
    );
    delegatedResourceCapsule.setCdedBalanceForUcr(
        cdedBalance,
        now - 100L);
    dbManager.getDelegatedResourceStore().put(DelegatedResourceCapsule
        .createDbKey(ByteArray.fromHexString(OWNER_ADDRESS),
            ByteArray.fromHexString(RECEIVER_ADDRESS)), delegatedResourceCapsule);

    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getDelegatedContractForCpu(OWNER_ADDRESS, RECEIVER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(0);
    dbManager.getDynamicPropertiesStore().saveAllowSvmSolidity059(0);
    dbManager.getAccountStore().delete(receiver.createDbKey());
    receiver = new AccountCapsule(receiver.getAddress(), ByteString.EMPTY, AccountType.Normal);
    receiver.setAcquiredDelegatedCdedBalanceForUcr(10L);
    dbManager.getAccountStore().put(receiver.createDbKey(), receiver);
    receiver = dbManager.getAccountStore().get(receiver.createDbKey());
    Assert.assertEquals(10, receiver.getAcquiredDelegatedCdedBalanceForUcr());

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals(e.getMessage(),
          "AcquiredDelegatedCdedBalanceForUcr[10] < delegatedUcr[1000000000]");
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    dbManager.getDynamicPropertiesStore().saveAllowShieldedTransaction(1);
    dbManager.getDynamicPropertiesStore().saveAllowSvmSolidity059(1);

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule ownerResult =
          dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(initBalance + cdedBalance, ownerResult.getBalance());
      Assert.assertEquals(0L, ownerResult.getStabilaPower());
      Assert.assertEquals(0L, ownerResult.getDelegatedCdedBalanceForUcr());
      receiver = dbManager.getAccountStore().get(receiver.createDbKey());
      Assert.assertEquals(0, receiver.getAcquiredDelegatedCdedBalanceForUcr());
    } catch (ContractValidateException e) {
      Assert.fail();
    } catch (ContractExeException e) {
      Assert.fail();
    }
  }

  @Test
  public void invalidOwnerAddress() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCded(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS_INVALID));
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
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCded(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ACCOUNT_INVALID));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ACCOUNT_INVALID + "] does not exist",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void noCdedBalance() {
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("no cdedBalance(BANDWIDTH)", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void notTimeToUncd() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCded(1_000_000_000L, now + 60000);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("cannot run here.");

    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("It's not time to uncd(BANDWIDTH).", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void testClearVotes() {
    byte[] ownerAddressBytes = ByteArray.fromHexString(OWNER_ADDRESS);
    ByteString ownerAddress = ByteString.copyFrom(ownerAddressBytes);
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddressBytes);
    accountCapsule.setCded(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForBandwidth(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    dbManager.getVotesStore().reset();
    Assert.assertNull(dbManager.getVotesStore().get(ownerAddressBytes));
    try {
      actuator.validate();
      actuator.execute(ret);
      VotesCapsule votesCapsule = dbManager.getVotesStore().get(ownerAddressBytes);
      Assert.assertNotNull(votesCapsule);
      Assert.assertEquals(0, votesCapsule.getNewVotes().size());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    // if had votes
    List<Vote> oldVotes = new ArrayList<Vote>();
    VotesCapsule votesCapsule = new VotesCapsule(
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), oldVotes);
    votesCapsule.addNewVotes(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
        100);
    dbManager.getVotesStore().put(ByteArray.fromHexString(OWNER_ADDRESS), votesCapsule);
    accountCapsule.setCded(1_000_000_000L, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    try {
      actuator.validate();
      actuator.execute(ret);
      votesCapsule = dbManager.getVotesStore().get(ownerAddressBytes);
      Assert.assertNotNull(votesCapsule);
      Assert.assertEquals(0, votesCapsule.getNewVotes().size());
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /*@Test
  public void InvalidTotalNetWeight(){
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    dbManager.getDynamicPropertiesStore().saveTotalNetWeight(smallTatalResource);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
            .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCded(cdedBalance, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    Assert.assertTrue(cdedBalance/1000_000L > smallTatalResource );
    UncdBalanceActuator actuator = new UncdBalanceActuator(
            getContract(OWNER_ADDRESS), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      Assert.assertTrue(dbManager.getDynamicPropertiesStore().getTotalNetWeight() >= 0);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }
  }

  @Test
  public void InvalidTotalUcrWeight(){
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    dbManager.getDynamicPropertiesStore().saveTotalUcrWeight(smallTatalResource);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
            .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCdedForUcr(cdedBalance, now);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    Assert.assertTrue(cdedBalance/1000_000L > smallTatalResource );
    UncdBalanceActuator actuator = new UncdBalanceActuator(
            getContract(OWNER_ADDRESS, Contract.ResourceCode.UCR), dbManager);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      Assert.assertTrue(dbManager.getDynamicPropertiesStore().getTotalUcrWeight() >= 0);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }
  }*/


  @Test
  public void commonErrorCheck() {
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error, expected type [UncdBalanceContract], real type[");
    actuatorTest.invalidContractType();

    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCded(cdedBalance, now);
    Assert.assertEquals(accountCapsule.getCdedBalance(), cdedBalance);
    Assert.assertEquals(accountCapsule.getStabilaPower(), cdedBalance);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    actuatorTest.setContract(getContractForBandwidth(OWNER_ADDRESS));
    actuatorTest.nullTransationResult();

    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }


  @Test
  public void testUncdBalanceForUcrWithOldStabilaPowerAfterNewResourceModel() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCdedForUcr(cdedBalance, now);
    accountCapsule.setOldStabilaPower(cdedBalance);
    accountCapsule.addVotes(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), 100L);
    Assert.assertEquals(accountCapsule.getAllCdedBalanceForUcr(), cdedBalance);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForCpu(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getVotesList().size(), 0L);
      Assert.assertEquals(owner.getInstance().getOldStabilaPower(), -1L);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testUncdBalanceForUcrWithoutOldStabilaPowerAfterNewResourceModel() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCdedForUcr(cdedBalance, now);
    accountCapsule.setOldStabilaPower(-1L);
    accountCapsule.addVotes(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), 100L);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForCpu(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getVotesList().size(), 1L);
      Assert.assertEquals(owner.getInstance().getOldStabilaPower(), -1L);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testUncdBalanceForStabilaPowerWithOldStabilaPowerAfterNewResourceModel() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCdedForUcr(cdedBalance, now);
    accountCapsule.setCdedForStabilaPower(cdedBalance, now);
    accountCapsule.addVotes(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), 100L);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForStabilaPower(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));

      Assert.assertEquals(owner.getVotesList().size(), 0L);
      Assert.assertEquals(owner.getInstance().getOldStabilaPower(), -1L);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void testUncdBalanceForStabilaPowerWithOldStabilaPowerAfterNewResourceModelError() {
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    dbManager.getDynamicPropertiesStore().saveAllowNewResourceModel(1L);

    AccountCapsule accountCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(OWNER_ADDRESS));
    accountCapsule.setCdedForUcr(cdedBalance, now);
    accountCapsule.setCdedForStabilaPower(cdedBalance, now + 100000000L);
    accountCapsule.addVotes(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), 100L);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdBalanceActuator actuator = new UncdBalanceActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContractForStabilaPower(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
    }
  }

}

