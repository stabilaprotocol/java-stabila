package org.stabila.core.actuator;

import static org.testng.Assert.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.core.ChainBaseManager;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountAssetCapsule;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.AssetIssueCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.Parameter.ForkBlockVersionConsts;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.AccountContract.AccountCreateContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.AssetIssueContract.CdedSupply;

@Slf4j
public class AssetIssueActuatorTest {

  private static final String dbPath = "output_assetIssue_test";
  private static final String OWNER_ADDRESS;
  private static final String OWNER_ADDRESS_SECOND;
  private static final String NAME = "stb-my";
  private static final long TOTAL_SUPPLY = 10000L;
  private static final int STB_NUM = 10000;
  private static final int NUM = 100000;
  private static final String DESCRIPTION = "myCoin";
  private static final String URL = "stabila-my.com";
  private static final String ASSET_NAME_SECOND = "asset_name2";
  private static StabilaApplicationContext context;
  private static Manager dbManager;
  private static ChainBaseManager chainBaseManager;
  private static long now = 0;
  private static long startTime = 0;
  private static long endTime = 0;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
    OWNER_ADDRESS_SECOND = Wallet
        .getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    chainBaseManager = context.getBean(ChainBaseManager.class);
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

    AccountAssetCapsule ownerAddressFirstAsset =
            new AccountAssetCapsule(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
    AccountAssetCapsule ownerAddressSecondAsset =
            new AccountAssetCapsule(
                    ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)));
    dbManager.getAccountAssetStore().put(ownerAddressFirstAsset.getAddress().toByteArray(),
            ownerAddressFirstAsset);
    dbManager.getAccountAssetStore().put(ownerAddressSecondAsset.getAddress().toByteArray(),
            ownerAddressSecondAsset);

    AccountCapsule ownerCapsule = new AccountCapsule(ByteString.copyFromUtf8("owner"),
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), AccountType.Normal,
        dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    AccountCapsule ownerSecondCapsule = new AccountCapsule(ByteString.copyFromUtf8("ownerSecond"),
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)), AccountType.Normal,
        dbManager.getDynamicPropertiesStore().getAssetIssueFee());
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
    dbManager.getAccountStore()
        .put(ownerSecondCapsule.getAddress().toByteArray(), ownerSecondCapsule);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(24 * 3600 * 1000);
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);

    now = chainBaseManager.getHeadBlockTimeStamp();
    startTime = now + 48 * 3600 * 1000;
    endTime = now + 72 * 3600 * 1000;
  }

  @After
  public void removeCapsule() {
    byte[] address = ByteArray.fromHexString(OWNER_ADDRESS);
    dbManager.getAccountStore().delete(address);
  }

  private Any getContract() {
    long nowTime = new Date().getTime();
    return Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).setPrecision(6)
            .build());
  }

  /**
   * SameTokenName close, asset issue success
   */
  @Test
  public void SameTokenNameCloseAssetIssueSuccess() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(getContract());

    TransactionResultCapsule ret = new TransactionResultCapsule();
    Long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      // check V1
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteString.copyFromUtf8(NAME).toByteArray());
      Assert.assertNotNull(assetIssueCapsule);
      Assert.assertEquals(6, assetIssueCapsule.getPrecision());
      Assert.assertEquals(NUM, assetIssueCapsule.getNum());
      Assert.assertEquals(STB_NUM, assetIssueCapsule.getStbNum());
      Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
      // check V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueV2Store()
          .get(ByteArray.fromString(String.valueOf(tokenIdNum)));
      Assert.assertNotNull(assetIssueCapsuleV2);
      Assert.assertEquals(0, assetIssueCapsuleV2.getPrecision());
      Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
      Assert.assertEquals(STB_NUM, assetIssueCapsuleV2.getStbNum());
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          TOTAL_SUPPLY);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /**
   * Init close SameTokenName,after init data,open SameTokenName
   */
  @Test
  public void oldNotUpdateAssetIssueSuccess() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(getContract());

    TransactionResultCapsule ret = new TransactionResultCapsule();
    Long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      // V1,Data is no longer update
      Assert.assertFalse(
          dbManager.getAssetIssueStore().has(ByteString.copyFromUtf8(NAME).toByteArray()));
      // check V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueV2Store()
          .get(ByteArray.fromString(String.valueOf(tokenIdNum)));
      Assert.assertNotNull(assetIssueCapsuleV2);
      Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
      Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
      Assert.assertEquals(STB_NUM, assetIssueCapsuleV2.getStbNum());
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          TOTAL_SUPPLY);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /**
   * SameTokenName open, asset issue success
   */
  @Test
  public void SameTokenNameOpenAssetIssueSuccess() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(getContract());

    TransactionResultCapsule ret = new TransactionResultCapsule();
    Long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      // V1,Data is no longer update
      Assert.assertFalse(
          dbManager.getAssetIssueStore().has(ByteString.copyFromUtf8(NAME).toByteArray()));
      // V2
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      byte[] assertKey = ByteArray.fromString(String.valueOf(tokenIdNum));
      AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueV2Store().get(assertKey);
      Assert.assertNotNull(assetIssueCapsuleV2);
      Assert.assertEquals(6, assetIssueCapsuleV2.getPrecision());
      Assert.assertEquals(NUM, assetIssueCapsuleV2.getNum());
      Assert.assertEquals(STB_NUM, assetIssueCapsuleV2.getStbNum());
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          TOTAL_SUPPLY);

    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /**
   * Total supply must greater than zero.Else can't asset issue and balance do not
   * change.
   */
  public void negativeTotalSupplyTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(-TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("TotalSupply must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /**
   * Total supply must greater than zero.Else can't asset issue and balance do not
   * change.
   */
  public void zeroTotalSupplyTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(0).setStbNum(STB_NUM).setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("TotalSupply must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
   * Stb num must greater than zero.Else can't asset issue and balance do not
   * change.
   */
  public void negativeStbNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(-STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("StbNum must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
   * Stb num must greater than zero.Else can't asset issue and balance do not
   * change.
   */
  public void zeroStbNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(0)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("StbNum must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
   * Num must greater than zero.Else can't asset issue and balance do not change.
   */
  public void negativeNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(-NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Num must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
   * Stb num must greater than zero.Else can't asset issue and balance do not
   * change.
   */
  public void zeroNumTest() {
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(0)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertTrue("Num must greater than 0!".equals(e.getMessage()));
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  @Test
  /*
   * Asset name length must between 1 to 32 and can not contain space and other
   * unreadable character, and can not contain chinese characters.
   */
  public void assetNameTest() {
    long nowTime = new Date().getTime();

    // Empty name, throw exception
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.EMPTY).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM).setNum(NUM)
            .setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // Too long name, throw exception. Max long is 32.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklmo"))
            .setTotalSupply(TOTAL_SUPPLY)
            .setStbNum(STB_NUM).setNum(NUM).setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // Contain space, throw exception. Every character need readable .
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8("t e")).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // Contain chinese character, throw exception.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFrom(ByteArray.fromHexString("E6B58BE8AF95")))
            .setTotalSupply(TOTAL_SUPPLY)
            .setStbNum(STB_NUM).setNum(NUM).setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid assetName", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // 32 byte readable character just ok.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8("testname0123456789abcdefghijgklm"))
            .setTotalSupply(TOTAL_SUPPLY)
            .setStbNum(STB_NUM).setNum(NUM).setStartTime(nowTime)
            .setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get("testname0123456789abcdefghijgklm".getBytes());
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get("testname0123456789abcdefghijgklm").longValue(),
          TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte readable character ok.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8("0")).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get("0".getBytes());
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get("0").longValue(), TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /*
   * Url length must between 1 to 256.
   */
  @Test
  public void urlTest() {
    long nowTime = new Date().getTime();

    // Empty url, throw exception
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION)).setUrl(ByteString.EMPTY).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid url", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    String url256Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef012345678"
        + "9abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0"
        +
        "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef012345678"
        + "9abcdef";
    // Too long url, throw exception. Max long is 256.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(url256Bytes + "0"))
            .build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid url", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // 256 byte readable character just ok.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(url256Bytes)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(NAME.getBytes());
      Assert.assertNotNull(assetIssueCapsule);
      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte url.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8("0")).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(NAME.getBytes());
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte space ok.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(" ")).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(NAME.getBytes());
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /*
   * Description length must less than 200.
   */
  @Test
  public void descriptionTest() {
    long nowTime = new Date().getTime();

    String description200Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0"
        + "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef012345678"
        + "9abcdef0123456789abcdef0123456789abcdef01234567";
    // Too long description, throw exception. Max long is 200.
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(description200Bytes + "0"))
            .setUrl(ByteString.copyFromUtf8(URL))
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid description", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // 200 bytes character just ok.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(description200Bytes))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(NAME.getBytes());
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // Empty description is ok.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.EMPTY)
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(NAME.getBytes());
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    createCapsule();
    // 1 byte space ok.
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(" "))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore().get(NAME.getBytes());
      Assert.assertNotNull(assetIssueCapsule);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMap().get(NAME).longValue(), TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /*
   * Test CdedSupply, 1. cded_amount must greater than zero.
   */
  @Test
  public void cdedTest() {
    // cded_amount = 0 throw exception.
    CdedSupply cdedSupply = CdedSupply.newBuilder().setCdedDays(1).setCdedAmount(0)
        .build();
    long nowTime = new Date().getTime();
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addCdedSupply(cdedSupply).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Cded supply must be greater than 0!", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // cded_amount < 0 throw exception.
    cdedSupply = CdedSupply.newBuilder().setCdedDays(1).setCdedAmount(-1).build();
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addCdedSupply(cdedSupply).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Cded supply must be greater than 0!", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    long minCdedSupplyTime = dbManager.getDynamicPropertiesStore().getMinCdedSupplyTime();
    long maxCdedSupplyTime = dbManager.getDynamicPropertiesStore().getMaxCdedSupplyTime();

    // CdedDays = 0 throw exception.
    cdedSupply = CdedSupply.newBuilder().setCdedDays(0).setCdedAmount(1).build();
    nowTime = new Date().getTime();
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addCdedSupply(cdedSupply).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "cdedDuration must be less than " + maxCdedSupplyTime + " days " + "and more than "
              + minCdedSupplyTime + " days", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // CdedDays < 0 throw exception.
    cdedSupply = CdedSupply.newBuilder().setCdedDays(-1).setCdedAmount(1).build();
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addCdedSupply(cdedSupply).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "cdedDuration must be less than " + maxCdedSupplyTime + " days " + "and more than "
              + minCdedSupplyTime + " days", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // CdedDays > maxCdedSupplyTime throw exception.
    cdedSupply = CdedSupply.newBuilder().setCdedDays(maxCdedSupplyTime + 1)
        .setCdedAmount(1).build();
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addCdedSupply(cdedSupply).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "cdedDuration must be less than " + maxCdedSupplyTime + " days " + "and more than "
              + minCdedSupplyTime + " days", e.getMessage());
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      AssetIssueCapsule assetIssueCapsule = dbManager.getAssetIssueStore()
          .get(ByteArray.fromString(NAME));
      Assert.assertEquals(owner.getBalance(),
          dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert
          .assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(), blackholeBalance);
      Assert.assertNull(assetIssueCapsule);
      Assert.assertNull(owner.getInstance().getAssetMap().get(NAME));
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }

    // cded_amount = 1 and cdedDays = 1 is OK
    cdedSupply = CdedSupply.newBuilder().setCdedDays(1).setCdedAmount(1).build();
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .addCdedSupply(cdedSupply).build());

    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();
    blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();

    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /**
   * 1. start time should not be null 2. end time should not be null 3. start time >=
   * getHeadBlockTimeStamp 4. start time < end time
   */
  @Test
  public void issueTimeTest() {
    // empty start time will throw exception
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setEndTime(endTime).setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "Start time should be not empty",
        "Start time should be not empty");

    // empty end time will throw exception
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(startTime).setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "End time should be not empty",
        "End time should be not empty");

    // startTime == now, throw exception
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(now).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "Start time should be greater than HeadBlockTime",
        "Start time should be greater than HeadBlockTime");

    // startTime < now, throw exception
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(now - 1).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "Start time should be greater than HeadBlockTime",
        "Start time should be greater than HeadBlockTime");

    // endTime == startTime, throw exception
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(startTime).setEndTime(startTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "End time should be greater than start time",
        "End time should be greater than start time");

    // endTime < startTime, throw exception
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(endTime).setEndTime(startTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "End time should be greater than start time",
        "End time should be greater than start time");

    // right issue, will not throw exception
    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(startTime).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();

    try {
      actuator.validate();
      actuator.execute(ret);
      AccountCapsule account = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      Assert.assertEquals(account.getAssetIssuedName().toStringUtf8(), NAME);
      Assert.assertEquals(account.getAssetMap().size(), 1);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /**
   * an account should issue asset only once
   */
  @Test
  public void assetIssueNameTest() {
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(startTime).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(ASSET_NAME_SECOND)).setTotalSupply(TOTAL_SUPPLY)
            .setStbNum(STB_NUM)
            .setNum(NUM).setStartTime(startTime).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("An account can only issue one asset", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(ASSET_NAME_SECOND));
    }
  }

  @Test
  public void assetIssueSTBNameTest() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8("STB")).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(startTime).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).build());
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("assetName can't be stb", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(ASSET_NAME_SECOND));
    }
  }

  @Test
  public void cdedListSizeTest() {
    this.dbManager.getDynamicPropertiesStore().saveMaxCdedSupplyNumber(3);
    List<CdedSupply> cdedList = new ArrayList();
    for (int i = 0; i < this.dbManager.getDynamicPropertiesStore()
        .getMaxCdedSupplyNumber() + 2; i++) {
      cdedList.add(CdedSupply.newBuilder().setCdedAmount(10).setCdedDays(3).build());
    }

    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(startTime).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).addAllCdedSupply(cdedList).build());
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "Cded supply list length is too long",
        "Cded supply list length is too long");

  }

  @Test
  public void cdedSupplyMoreThanTotalSupplyTest() {
    this.dbManager.getDynamicPropertiesStore().saveMaxCdedSupplyNumber(3);
    List<CdedSupply> cdedList = new ArrayList();
    cdedList
        .add(CdedSupply.newBuilder().setCdedAmount(TOTAL_SUPPLY + 1).setCdedDays(3).build());
    Any contract = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(startTime).setEndTime(endTime)
            .setDescription(ByteString.copyFromUtf8("description"))
            .setUrl(ByteString.copyFromUtf8(URL)).addAllCdedSupply(cdedList).build());
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(contract);

    TransactionResultCapsule ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "Cded supply cannot exceed total supply",
        "Cded supply cannot exceed total supply");

  }

  /**
   * SameTokenName close, Invalid ownerAddress
   */
  @Test
  public void SameTokenNameCloseInvalidOwnerAddress() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    long nowTime = new Date().getTime();
    Any any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString("12312315345345")))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    TransactionResultCapsule ret = new TransactionResultCapsule();

    processAndCheckInvalid(actuator, ret, "Invalid ownerAddress", "Invalid ownerAddress");

  }

  /**
   * SameTokenName open, check invalid precision
   */
  @Test
  public void SameTokenNameCloseInvalidPrecision() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    long nowTime = new Date().getTime();
    Any any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).setPrecision(7)
            .build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    dbManager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionConsts.UCR_LIMIT, stats);
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);

    processAndCheckInvalid(actuator, ret, "precision cannot exceed 6", "precision cannot exceed 6");

  }

  /**
   * SameTokenName close, Invalid abbreviation for token
   */
  @Test
  public void SameTokenNameCloseInvalidAddr() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    long nowTime = new Date().getTime();
    Any any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL))
            .setAbbr(ByteString
                .copyFrom(ByteArray.fromHexString("a0299f3db80a24123b20a254b89ce639d59132f157f13")))
            .setPrecision(4).build());

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    TransactionResultCapsule ret = new TransactionResultCapsule();
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    dbManager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionConsts.UCR_LIMIT, stats);

    processAndCheckInvalid(actuator, ret, "Invalid abbreviation for token",
        "Invalid abbreviation for token");

  }

  /**
   * repeat issue assert name,
   */
  @Test
  public void IssueSameTokenNameAssert() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    String ownerAddress = "a08beaa1a8e2d45367af7bae7c49009876a4fa4301";

    long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
    dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
    AssetIssueContract assetIssueContract = AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)))
        .setName(ByteString.copyFrom(ByteArray.fromString(NAME))).setId(Long.toString(id))
        .setTotalSupply(TOTAL_SUPPLY)
        .setStbNum(STB_NUM).setNum(NUM).setStartTime(1).setEndTime(100).setVoteScore(2)
        .setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
        .setUrl(ByteString.copyFrom(ByteArray.fromString(URL))).build();
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
    dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

    AccountCapsule ownerCapsule = new AccountCapsule(
        ByteString.copyFrom(ByteArray.fromHexString(ownerAddress)),
        ByteString.copyFromUtf8("owner11"), AccountType.AssetIssue);
    ownerCapsule.addAsset(NAME.getBytes(), 1000L);
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(getContract());

    TransactionResultCapsule ret = new TransactionResultCapsule();
    Long blackholeBalance = dbManager.getAccountStore().getBlackhole().getBalance();
    // SameTokenName not active, same assert name, should failure

    processAndCheckInvalid(actuator, ret, "Token exists", "Token exists");

    // SameTokenName active, same assert name,should success
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      AssetIssueCapsule assetIssueCapsuleV2 = dbManager.getAssetIssueV2Store()
          .get(ByteArray.fromString(String.valueOf(tokenIdNum)));
      Assert.assertNotNull(assetIssueCapsuleV2);

      Assert.assertEquals(owner.getBalance(), 0L);
      Assert.assertEquals(dbManager.getAccountStore().getBlackhole().getBalance(),
          blackholeBalance + dbManager.getDynamicPropertiesStore().getAssetIssueFee());
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenIdNum)).longValue(),
          TOTAL_SUPPLY);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

  /**
   * SameTokenName close, check invalid param "PublicFreeAssetNetUsage must be 0!" "Invalid
   * FreeAssetNetLimit" "Invalid PublicFreeAssetNetLimit" "Account not exists" "No enough balance
   * for fee!"
   */
  @Test
  public void SameTokenNameCloseInvalidparam() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    long nowTime = new Date().getTime();
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    dbManager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionConsts.UCR_LIMIT, stats);
    TransactionResultCapsule ret = new TransactionResultCapsule();

    // PublicFreeAssetNetUsage must be 0!
    Any any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).setPrecision(3)
            .setPublicFreeAssetNetUsage(100).build());
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    processAndCheckInvalid(actuator, ret, "PublicFreeAssetNetUsage must be 0!",
        "PublicFreeAssetNetUsage must be 0!");

    // Invalid FreeAssetNetLimit
    any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).setPrecision(3)
            .setFreeAssetNetLimit(-10).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    processAndCheckInvalid(actuator, ret, "Invalid FreeAssetNetLimit", "Invalid FreeAssetNetLimit");

    // Invalid PublicFreeAssetNetLimit
    any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).setPrecision(3)
            .setPublicFreeAssetNetLimit(-10).build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    processAndCheckInvalid(actuator, ret, "Invalid PublicFreeAssetNetLimit",
        "Invalid PublicFreeAssetNetLimit");

  }

  /**
   * SameTokenName close, account not good "Account not exists" "No enough balance for fee!"
   */
  @Test
  public void SameTokenNameCloseInvalidAccount() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);
    long nowTime = new Date().getTime();
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    dbManager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionConsts.UCR_LIMIT, stats);
    TransactionResultCapsule ret = new TransactionResultCapsule();

    // No enough balance for fee!
    Any any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).setPrecision(3)
            .build());
    AssetIssueActuator actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
    owner.setBalance(1000);
    dbManager.getAccountStore().put(owner.createDbKey(), owner);

    processAndCheckInvalid(actuator, ret, "No enough balance for fee!",
        "No enough balance for fee!");

    // Account not exists
    dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS));
    any = Any.pack(
        AssetIssueContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
            .setName(ByteString.copyFromUtf8(NAME)).setTotalSupply(TOTAL_SUPPLY).setStbNum(STB_NUM)
            .setNum(NUM)
            .setStartTime(nowTime).setEndTime(nowTime + 24 * 3600 * 1000)
            .setDescription(ByteString.copyFromUtf8(DESCRIPTION))
            .setUrl(ByteString.copyFromUtf8(URL)).setPrecision(3)
            .build());
    actuator = new AssetIssueActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager()).setAny(any);

    processAndCheckInvalid(actuator, ret, "Account not exists", "Account not exists");

  }


  @Test
  public void commonErrorCheck() {

    AssetIssueActuator actuator = new AssetIssueActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any InvalidContract = Any.pack(AccountCreateContract.newBuilder().build());
    actuatorTest.setInvalidContract(InvalidContract);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error,expected type [AssetIssueContract],real type[");
    actuatorTest.invalidContractType();

    actuatorTest.setContract(getContract());
    actuatorTest.nullTransationResult();

    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();

  }

  private void processAndCheckInvalid(AssetIssueActuator actuator, TransactionResultCapsule ret,
      String failMsg,
      String expectedMsg) {
    try {
      actuator.validate();
      actuator.execute(ret);

      fail(failMsg);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(expectedMsg, e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } catch (RuntimeException e) {
      Assert.assertTrue(e instanceof RuntimeException);
      Assert.assertEquals(expectedMsg, e.getMessage());
    } finally {
      dbManager.getAssetIssueStore().delete(ByteArray.fromString(NAME));
    }
  }

}