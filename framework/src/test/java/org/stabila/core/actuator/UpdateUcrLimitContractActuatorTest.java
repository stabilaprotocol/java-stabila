package org.stabila.core.actuator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.ContractCapsule;
import org.stabila.core.capsule.TransactionResultCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.Parameter.ForkBlockVersionConsts;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.StabilaException;
import org.stabila.core.vm.config.VMConfig;
import org.stabila.protos.Protocol;
import org.stabila.protos.contract.AssetIssueContractOuterClass;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.UpdateUcrLimitContract;


@Slf4j
//@Ignore
public class UpdateUcrLimitContractActuatorTest {

  private static final String dbPath = "output_updateUcrLimitContractActuator_test";
  private static final String OWNER_ADDRESS_ACCOUNT_NAME = "test_account";
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String SMART_CONTRACT_NAME = "smart_contarct";
  private static final String CONTRACT_ADDRESS = "111111";
  private static final String NO_EXIST_CONTRACT_ADDRESS = "2222222";
  private static final long SOURCE_UCR_LIMIT = 10L;
  private static final long TARGET_UCR_LIMIT = 30L;
  private static final long INVALID_UCR_LIMIT = -200L;
  private static StabilaApplicationContext context;
  private static Manager dbManager;
  private static String OWNER_ADDRESS;
  private static String SECOND_ACCOUNT_ADDRESS;
  private static String OWNER_ADDRESS_NOTEXIST;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    OWNER_ADDRESS =
        Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    SECOND_ACCOUNT_ADDRESS =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d427122222";
    OWNER_ADDRESS_NOTEXIST =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";

    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    dbManager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionConsts.UCR_LIMIT, stats);
    VMConfig.initVmHardFork(true);
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
    CommonParameter.setUCR_LIMIT_HARD_FORK(false);
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    // address in accountStore and the owner of contract
    AccountCapsule accountCapsule =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore().put(ByteArray.fromHexString(OWNER_ADDRESS), accountCapsule);

    // smartContract in contractStore
    SmartContract.Builder builder = SmartContract.newBuilder();
    builder.setName(SMART_CONTRACT_NAME);
    builder.setOriginAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)));
    builder.setContractAddress(ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_ADDRESS)));
    builder.setOriginUcrLimit(SOURCE_UCR_LIMIT);
    dbManager.getContractStore().put(
        ByteArray.fromHexString(CONTRACT_ADDRESS),
        new ContractCapsule(builder.build()));

    // address in accountStore not the owner of contract
    AccountCapsule secondAccount =
        new AccountCapsule(
            ByteString.copyFrom(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS)),
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            Protocol.AccountType.Normal);
    dbManager.getAccountStore().put(ByteArray.fromHexString(SECOND_ACCOUNT_ADDRESS), secondAccount);

    // address does not exist in accountStore
    dbManager.getAccountStore().delete(ByteArray.fromHexString(OWNER_ADDRESS_NOTEXIST));
  }

  private Any getContract(String accountAddress, String contractAddress, long originUcrLimit) {
    return Any.pack(
        UpdateUcrLimitContract.newBuilder()
            .setOwnerAddress(StringUtil.hexString2ByteString(accountAddress))
            .setContractAddress(StringUtil.hexString2ByteString(contractAddress))
            .setOriginUcrLimit(originUcrLimit).build());
  }

  @Test
  public void successUpdateUcrLimitContract() throws InvalidProtocolBufferException {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_UCR_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      // assert result state and ucr_limit
      Assert.assertEquals(OWNER_ADDRESS,
          ByteArray.toHexString(actuator.getOwnerAddress().toByteArray()));
      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getOriginUcrLimit(), TARGET_UCR_LIMIT);
    } catch (ContractValidateException | ContractExeException e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void invalidAddress() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_INVALID, CONTRACT_ADDRESS, TARGET_UCR_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Invalid address");
    } catch (StabilaException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
    }
  }

  @Test
  public void noExistAccount() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS_NOTEXIST, CONTRACT_ADDRESS, TARGET_UCR_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Account[" + OWNER_ADDRESS_NOTEXIST + "] not exists");
    } catch (StabilaException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Account[" + OWNER_ADDRESS_NOTEXIST + "] does not exist", e.getMessage());
    }
  }

  @Test
  public void invalidResourceUcrLimit() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, INVALID_UCR_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("origin ucr limit less than 0");
    } catch (StabilaException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("origin ucr limit must be > 0", e.getMessage());
    }
  }

  @Test
  public void noExistContract() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, NO_EXIST_CONTRACT_ADDRESS, TARGET_UCR_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Contract not exists");
    } catch (StabilaException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Contract does not exist", e.getMessage());
    }
  }

  @Test
  public void callerNotContractOwner() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(SECOND_ACCOUNT_ADDRESS, CONTRACT_ADDRESS, TARGET_UCR_LIMIT));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      actuator.validate();
      actuator.execute(ret);

      fail("Account[" + SECOND_ACCOUNT_ADDRESS + "] is not the owner of the contract");
    } catch (StabilaException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals(
          "Account[" + SECOND_ACCOUNT_ADDRESS + "] is not the owner of the contract",
          e.getMessage());
    }
  }

  @Test
  public void twiceUpdateUcrLimitContract() throws InvalidProtocolBufferException {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_UCR_LIMIT));

    UpdateUcrLimitContractActuator secondActuator = new UpdateUcrLimitContractActuator();
    secondActuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, 90L));

    TransactionResultCapsule ret = new TransactionResultCapsule();
    try {
      // first
      actuator.validate();
      actuator.execute(ret);

      Assert.assertEquals(OWNER_ADDRESS,
          ByteArray.toHexString(actuator.getOwnerAddress().toByteArray()));
      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getOriginUcrLimit(), TARGET_UCR_LIMIT);

      // second
      secondActuator.validate();
      secondActuator.execute(ret);

      Assert.assertEquals(ret.getInstance().getRet(), Protocol.Transaction.Result.code.SUCESS);
      Assert.assertEquals(
          dbManager.getContractStore().get(ByteArray.fromHexString(CONTRACT_ADDRESS))
              .getOriginUcrLimit(), 90L);
    } catch (ContractValidateException | ContractExeException e) {
      Assert.fail(e.getMessage());
    }
  }


  @Test
  public void nullDBManger() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(null)
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_UCR_LIMIT));
    TransactionResultCapsule ret = new TransactionResultCapsule();
    processAndCheckInvalid(actuator, ret, "No account store or dynamic store!",
        "No account store or dynamic store!");
  }

  @Test
  public void noContract() {

    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(null);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    processAndCheckInvalid(actuator, ret, "No contract!", "No contract!");
  }

  @Test
  public void invalidContractType() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    // create AssetIssueContract, not a valid UpdateUcrLimitContract contract , which will
    // throw e exception
    Any invalidContractTypes = Any.pack(AssetIssueContractOuterClass.AssetIssueContract.newBuilder()
        .build());
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(invalidContractTypes);
    TransactionResultCapsule ret = new TransactionResultCapsule();
    processAndCheckInvalid(actuator, ret, "contract type error",
        "contract type error, expected type [UpdateUcrLimitContract],real type["
            + invalidContractTypes.getClass() + "]");
  }

  @Test
  public void nullTransactionResult() {
    UpdateUcrLimitContractActuator actuator = new UpdateUcrLimitContractActuator();
    actuator.setChainBaseManager(dbManager.getChainBaseManager())
        .setAny(getContract(OWNER_ADDRESS, CONTRACT_ADDRESS, TARGET_UCR_LIMIT));
    TransactionResultCapsule ret = null;
    processAndCheckInvalid(actuator, ret, "TransactionResultCapsule is null",
        "TransactionResultCapsule is null");
  }

  private void processAndCheckInvalid(UpdateUcrLimitContractActuator actuator,
                                      TransactionResultCapsule ret,
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
    }
  }


}
