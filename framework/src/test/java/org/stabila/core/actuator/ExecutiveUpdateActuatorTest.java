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
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Transaction.Result.code;
import org.stabila.protos.contract.AssetIssueContractOuterClass;
import org.stabila.protos.contract.ExecutiveContract.ExecutiveUpdateContract;

@Slf4j
public class ExecutiveUpdateActuatorTest {

  private static final String dbPath = "output_ExecutiveUpdate_test";
  private static final String OWNER_ADDRESS;
  private static final String OWNER_ADDRESS_ACCOUNT_NAME = "test_account";
  private static final String OWNER_ADDRESS_NOT_EXECUTIVE;
  private static final String OWNER_ADDRESS_NOT_EXECUTIVE_ACCOUNT_NAME = "test_account1";
  private static final String OWNER_ADDRESS_NOTEXIST;
  private static final String URL = "https://stabila.network";
  private static final String NewURL = "https://stabila.org";
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static StabilaApplicationContext context;
  private static Manager dbManager;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    OWNER_ADDRESS_NOTEXIST =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    OWNER_ADDRESS_NOT_EXECUTIVE =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
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
    // address in accountStore and executiveStore
    AccountCapsule accountCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS), accountCapsule);
    ExecutiveCapsule ownerCapsule = new ExecutiveCapsule(
        ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)), 10_000_000L, URL);
    dbManager.getExecutiveStore().put(ByteArray.fromHexString(OWNER_ADDRESS), ownerCapsule);

    // address exist in accountStore, but is not executive
    AccountCapsule accountNotExecutiveCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_NOT_EXECUTIVE)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_NOT_EXECUTIVE_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore()
        .put(ByteArray.fromHexString(OWNER_ADDRESS_NOT_EXECUTIVE), accountNotExecutiveCapsule);
    dbManager.getExecutiveStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_NOT_EXECUTIVE));

    // address does not exist in accountStore
    dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_NOTEXIST));
  }

  private Any getContract(String address, String url) {
    return Any.pack(
        ExecutiveUpdateContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
            .setUpdateUrl(ByteString.copyFrom(ByteArray.fromString(url)))
            .build());
  }

  private Any getContract(String address, ByteString url) {
    return Any.pack(
        ExecutiveUpdateContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
            .setUpdateUrl(url)
            .build());
  }

  /**
   * Update executive,result is success.
   */
  @Test
  public void rightUpdateExecutive() {
    ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, NewURL));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertNotNull(executiveCapsule);
      Assert.assertEquals(executiveCapsule.getUrl(), NewURL);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Invalid Address update executive,result is failed,exception is "Invalid address".
   */
  @Test
  public void InvalidAddress() {
    ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_INVALID, NewURL));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid address");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Invalid url createExecutive,result is failed,exception is "Invalid url".
   */
  @Test
  public void InvalidUrlTest() {
    TransactionResultCapsule ret = new TransactionResultCapsule();
    //Url cannot empty
    try {
      ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
      actuator.setChainBaseManager(dbManager.getChainBaseManager())
          .setAny(getContract(OWNER_ADDRESS, ByteString.EMPTY));
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid url");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid url", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    //256 bytes
    String url256Bytes = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef012345678"
        + "9abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0"
        + "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef012345678"
        + "9abcdef";
    //Url length can not greater than 256
    try {
      ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
      actuator.setChainBaseManager(dbManager.getChainBaseManager())
          .setAny(getContract(OWNER_ADDRESS, ByteString.copyFromUtf8(url256Bytes + "0")));
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid url");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid url", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    // 1 byte url is ok.
    try {
      ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
      actuator.setChainBaseManager(dbManager.getChainBaseManager())
          .setAny(getContract(OWNER_ADDRESS, "0"));
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertNotNull(executiveCapsule);
      Assert.assertEquals(executiveCapsule.getUrl(), "0");
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

    // 256 bytes url is ok.
    try {
      ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
      actuator.setChainBaseManager(dbManager.getChainBaseManager())
          .setAny(getContract(OWNER_ADDRESS, url256Bytes));
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      ExecutiveCapsule executiveCapsule = dbManager.getExecutiveStore()
          .get(ByteArray.fromHexString(OWNER_ADDRESS));
      Assert.assertNotNull(executiveCapsule);
      Assert.assertEquals(executiveCapsule.getUrl(), url256Bytes);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use AccountStore not exists Address createExecutive,result is failed,exception is "Executive does
   * not exist"
   */
  @Test
  public void notExistExecutive() {
    ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_NOT_EXECUTIVE, URL));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("executive [+OWNER_ADDRESS_NOACCOUNT+] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Executive does not exist", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * if account does not exist in accountStore, the test will throw a Exception
   */
  @Test
  public void notExistAccount() {
    ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_NOTEXIST, URL));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("account does not exist");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("account does not exist", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }


  @Test
  public void commonErrorCheck() {

    ExecutiveUpdateActuator actuator = new ExecutiveUpdateActuator();
    ActuatorTest actuatorTest = new ActuatorTest(actuator, dbManager);
    actuatorTest.noContract();

    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuatorTest.setInvalidContract(invalidContractTypes);
    actuatorTest.setInvalidContractTypeMsg("contract type error",
        "contract type error, expected type [ExecutiveUpdateContract],real type[");
    actuatorTest.invalidContractType();

    actuatorTest.setContract(getContract(OWNER_ADDRESS, NewURL));
    actuatorTest.nullTransationResult();

    actuatorTest.setNullDBManagerMsg("No account store or executive store!");
    actuatorTest.nullDBManger();

  }
}
