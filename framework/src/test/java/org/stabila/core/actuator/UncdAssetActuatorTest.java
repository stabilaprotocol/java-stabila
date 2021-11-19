package org.stabila.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountAssetCapsule;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.AssetIssueCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Account.Cded;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.AssetIssueContractOuterClass;
import org.stabila.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.stabila.protos.contract.AssetIssueContractOuterClass.UncdAssetContract;

@Slf4j
public class UncdAssetActuatorTest {

  private static final String dbPath = "output_uncd_asset_test";
  private static final String OWNER_ADDRESS;
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ACCOUNT_INVALID;
  private static final long initBalance = 10_000_000_000L;
  private static final long cdedBalance = 1_000_000_000L;
  private static final String assetName = "testCoin";
  private static final String assetID = "123456";
  private static Manager dbManager;
  private static StabilaApplicationContext context;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    OWNER_ACCOUNT_INVALID =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a3456";
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
  public void createAccountCapsule() {
  }

  @Before
  public void createAsset() {
  }

  private Any getContract(String ownerAddress) {
    return Any.pack(
        UncdAssetContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(ownerAddress))
            .build());
  }


  private void createAssertBeforSameTokenNameActive() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(0);

    long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    AssetIssueContract.Builder builder = AssetIssueContract.newBuilder();
    builder.setName(ByteString.copyFromUtf8(assetName));
    builder.setId(String.valueOf(tokenId));
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(builder.build());
    dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
    dbManager.getAssetIssueV2Store().put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);

    AccountAssetCapsule ownerAddressAsset =
            new AccountAssetCapsule(StringUtil.hexString2ByteString(OWNER_ADDRESS));
    dbManager.getAccountAssetStore().put(ownerAddressAsset.getAddress().toByteArray(),
            ownerAddressAsset);

    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("owner"),
            StringUtil.hexString2ByteString(OWNER_ADDRESS),
            AccountType.Normal,
            initBalance);
    ownerCapsule.setAssetIssuedName(assetName.getBytes());
    ownerCapsule.setAssetIssuedID(assetIssueCapsule.createDbV2Key());
    dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
  }

  private void createAssertSameTokenNameActive() {
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);

    long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    AssetIssueContract.Builder builder = AssetIssueContract.newBuilder();
    builder.setName(ByteString.copyFromUtf8(assetName));
    builder.setId(String.valueOf(tokenId));
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(builder.build());
    dbManager.getAssetIssueV2Store().put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);

    AccountAssetCapsule ownerAddressAsset =
            new AccountAssetCapsule(StringUtil.hexString2ByteString(OWNER_ADDRESS));
    dbManager.getAccountAssetStore().put(ownerAddressAsset.getAddress().toByteArray(),
            ownerAddressAsset);

    AccountCapsule ownerCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("owner"),
            StringUtil.hexString2ByteString(OWNER_ADDRESS),
            AccountType.Normal,
            initBalance);
    ownerCapsule.setAssetIssuedName(assetName.getBytes());
    ownerCapsule.setAssetIssuedID(assetIssueCapsule.createDbV2Key());
    dbManager.getAccountStore().put(ownerCapsule.createDbKey(), ownerCapsule);
  }

  /**
   * SameTokenName close, Uncd assert success.
   */
  @Test
  public void SameTokenNameCloseUncdAsset() {
    createAssertBeforSameTokenNameActive();
    long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);
    AccountCapsule ownerAccount = dbManager.getAccountStore()
            .get(ByteArray.fromHexString(OWNER_ADDRESS));
    ownerAccount.importAsset();
    Account account = ownerAccount
        .getInstance();
    Cded newCded0 = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(now)
        .build();
    Cded newCded1 = Cded.newBuilder()
        .setCdedBalance(cdedBalance + 1)
        .setExpireTime(now + 600000)
        .build();
    account = account.toBuilder().addCdedSupply(newCded0).addCdedSupply(newCded1).build();
    AccountCapsule accountCapsule = new AccountCapsule(account);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      //V1
      Assert.assertEquals(owner.getAssetMap().get(assetName).longValue(), cdedBalance);
      //V2
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenId)).longValue(),
          cdedBalance);
      Assert.assertEquals(owner.getCdedSupplyCount(), 1);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * SameTokenName active, Uncd assert success.
   */
  @Test
  public void SameTokenNameActiveUncdAsset() {
    createAssertSameTokenNameActive();
    long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule ownerAccount = dbManager.getAccountStore()
            .get(ByteArray.fromHexString(OWNER_ADDRESS));
    ownerAccount.importAsset();
    Account account = ownerAccount.getInstance();
    Cded newCded0 = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(now)
        .build();
    Cded newCded1 = Cded.newBuilder()
        .setCdedBalance(cdedBalance + 1)
        .setExpireTime(now + 600000)
        .build();
    account = account.toBuilder().addCdedSupply(newCded0).addCdedSupply(newCded1).build();
    AccountCapsule accountCapsule = new AccountCapsule(account);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      //V1 assert not exist
      Assert.assertNull(owner.getAssetMap().get(assetName));
      //V2
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenId)).longValue(),
          cdedBalance);
      Assert.assertEquals(owner.getCdedSupplyCount(), 1);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  /**
   * when init data, SameTokenName is close, then open SameTokenName, Uncd assert success.
   */
  @Test
  public void SameTokenNameActiveInitAndAcitveUncdAsset() {
    createAssertBeforSameTokenNameActive();
    dbManager.getDynamicPropertiesStore().saveAllowSameTokenName(1);
    long tokenId = dbManager.getDynamicPropertiesStore().getTokenIdNum();
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule ownerAccount = dbManager.getAccountStore()
            .get(ByteArray.fromHexString(OWNER_ADDRESS));
    ownerAccount.importAsset();
    Account account = ownerAccount
            .getInstance();
    Cded newCded0 = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(now)
        .build();
    Cded newCded1 = Cded.newBuilder()
        .setCdedBalance(cdedBalance + 1)
        .setExpireTime(now + 600000)
        .build();
    account = account.toBuilder().addCdedSupply(newCded0).addCdedSupply(newCded1).build();
    AccountCapsule accountCapsule = new AccountCapsule(account);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      AccountCapsule owner = dbManager.getAccountStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      //V1 assert not exist
      Assert.assertNull(owner.getAssetMap().get(assetName));
      //V2
      Assert.assertEquals(owner.getAssetMapV2().get(String.valueOf(tokenId)).longValue(),
          cdedBalance);
      Assert.assertEquals(owner.getCdedSupplyCount(), 1);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void invalidOwnerAddress() {
    createAssertBeforSameTokenNameActive();
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_INVALID));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertTrue(e instanceof ContractExeException);
    }
  }

  @Test
  public void invalidOwnerAccount() {
    createAssertBeforSameTokenNameActive();
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ACCOUNT_INVALID));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ACCOUNT_INVALID + "] does not exist",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void notIssueAsset() {
    createAssertBeforSameTokenNameActive();
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    Account account = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS))
        .getInstance();
    Cded newCded = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(now)
        .build();
    account = account.toBuilder().addCdedSupply(newCded).setAssetIssuedName(ByteString.EMPTY)
        .build();
    AccountCapsule accountCapsule = new AccountCapsule(account);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("this account has not issued any asset", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void noCdedSupply() {
    createAssertBeforSameTokenNameActive();
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("no cded supply balance", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  @Test
  public void notTimeToUncd() {
    createAssertBeforSameTokenNameActive();
    long now = System.currentTimeMillis();
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(now);

    AccountCapsule ownerAccount = dbManager.getAccountStore()
            .get(ByteArray.fromHexString(OWNER_ADDRESS));
    ownerAccount.importAsset();

    Account account = ownerAccount.getInstance();
    Cded newCded = Cded.newBuilder()
        .setCdedBalance(cdedBalance)
        .setExpireTime(now + 60000)
        .build();
    account = account.toBuilder().addCdedSupply(newCded).build();
    AccountCapsule accountCapsule = new AccountCapsule(account);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    UncdAssetActuator actuator = new UncdAssetActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("It's not time to uncd asset supply", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void commonErrorCheck() {
    createAssertSameTokenNameActive();
    UncdAssetActuator actuator = new UncdAssetActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error, expected type [UncdAssetContract], real type[");
    actuatorTest.invalidContractType();

    actuatorTest.setContract(getContract(OWNER_ADDRESS));

    actuatorTest.setNullDBManagerMsg("No account store or dynamic store!");
    actuatorTest.nullDBManger();
  }
}