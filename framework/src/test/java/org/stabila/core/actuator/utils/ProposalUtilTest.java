package org.stabila.core.actuator.utils;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.stabila.core.utils.ProposalUtil;
import org.stabila.common.application.Application;
import org.stabila.common.application.ApplicationFactory;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.ForkController;
import org.stabila.core.Constant;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.Parameter.ForkBlockVersionEnum;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.store.DynamicPropertiesStore;

@Slf4j(topic = "actuator")
public class ProposalUtilTest {

  private static final String dbPath = "output_ProposalUtil_test";
  private static final long LONG_VALUE = 100_000_000_000_000_000L;
  private static final String LONG_VALUE_ERROR =
      "Bad chain parameter value, valid range is [0," + LONG_VALUE + "]";
  public static Application AppT;
  private static StabilaApplicationContext context;
  private static Manager dbManager;

  /**
   * Init .
   */
  @BeforeClass
  public static void init() {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    dbManager = context.getBean(Manager.class);
    AppT = ApplicationFactory.create(context);
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

  @Test
  public void validProposalTypeCheck() throws ContractValidateException {

    Assert.assertEquals(false, ProposalUtil.ProposalType.contain(4000));
    Assert.assertEquals(false, ProposalUtil.ProposalType.contain(-1));
    Assert.assertEquals(true, ProposalUtil.ProposalType.contain(2));

    Assert.assertEquals(null, ProposalUtil.ProposalType.getEnumOrNull(-2));
    Assert.assertEquals(ProposalUtil.ProposalType.ALLOW_SVM_SOLIDITY_059, ProposalUtil.ProposalType.getEnumOrNull(32));

    long code = -1;
    try {
      ProposalUtil.ProposalType.getEnum(code);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals("Does not support code : " + code, e.getMessage());
    }

    code = 32;
    Assert.assertEquals(ProposalUtil.ProposalType.ALLOW_SVM_SOLIDITY_059, ProposalUtil.ProposalType.getEnum(code));

  }

  @Test
  public void validateCheck() {
    ProposalUtil actuatorUtil = new ProposalUtil();
    DynamicPropertiesStore dynamicPropertiesStore = null;
    ForkController forkUtils = ForkController.instance();
    long invalidValue = -1;

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ACCOUNT_UPGRADE_COST.getCode(), invalidValue);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ACCOUNT_UPGRADE_COST.getCode(), LONG_VALUE + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.CREATE_ACCOUNT_FEE.getCode(), invalidValue);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.CREATE_ACCOUNT_FEE.getCode(), LONG_VALUE + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ASSET_ISSUE_FEE.getCode(), invalidValue);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ASSET_ISSUE_FEE.getCode(), LONG_VALUE + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.EXECUTIVE_PAY_PER_BLOCK.getCode(), invalidValue);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.EXECUTIVE_PAY_PER_BLOCK.getCode(), LONG_VALUE + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.EXECUTIVE_STANDBY_ALLOWANCE.getCode(), invalidValue);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.EXECUTIVE_STANDBY_ALLOWANCE.getCode(), LONG_VALUE + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT.getCode(), invalidValue);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT.getCode(), LONG_VALUE + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.CREATE_NEW_ACCOUNT_BANDWIDTH_RATE.getCode(), invalidValue);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.CREATE_NEW_ACCOUNT_BANDWIDTH_RATE.getCode(), LONG_VALUE + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(LONG_VALUE_ERROR, e.getMessage());
    }

    long value = 32;
    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.MAINTENANCE_TIME_INTERVAL.getCode(), 3 * 27 * 1000 - 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "Bad chain parameter value, valid range is [3 * 27 * 1000,24 * 3600 * 1000]",
          e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.MAINTENANCE_TIME_INTERVAL.getCode(), 24 * 3600 * 1000 + 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "Bad chain parameter value, valid range is [3 * 27 * 1000,24 * 3600 * 1000]",
          e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ALLOW_CREATION_OF_CONTRACTS.getCode(), 2);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "This value[ALLOW_CREATION_OF_CONTRACTS] is only allowed to be 1",
          e.getMessage());
    }

    dynamicPropertiesStore = dbManager.getDynamicPropertiesStore();
    dynamicPropertiesStore.saveRemoveThePowerOfTheGr(1);
    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.REMOVE_THE_POWER_OF_THE_GR.getCode(), 2);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "This value[REMOVE_THE_POWER_OF_THE_GR] is only allowed to be 1",
          e.getMessage());
    }

    dynamicPropertiesStore.saveRemoveThePowerOfTheGr(-1);
    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.REMOVE_THE_POWER_OF_THE_GR.getCode(), 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "This proposal has been executed before and is only allowed to be executed once",
          e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.MAX_CPU_TIME_OF_ONE_TX.getCode(), 9);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "Bad chain parameter value, valid range is [10,100]", e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.MAX_CPU_TIME_OF_ONE_TX.getCode(), 101);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "Bad chain parameter value, valid range is [10,100]", e.getMessage());
    }

    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ALLOW_DELEGATE_RESOURCE.getCode(), 2);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "This value[ALLOW_DELEGATE_RESOURCE] is only allowed to be 1", e.getMessage());
    }

    dynamicPropertiesStore.saveAllowSameTokenName(1);
    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ALLOW_SVM_TRANSFER_SRC10.getCode(), 2);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals(
          "This value[ALLOW_SVM_TRANSFER_SRC10] is only allowed to be 1", e.getMessage());
    }

    dynamicPropertiesStore.saveAllowSameTokenName(0);
    try {
      actuatorUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ALLOW_SVM_TRANSFER_SRC10.getCode(), 1);
      Assert.assertTrue(false);
    } catch (ContractValidateException e) {
      Assert.assertEquals("[ALLOW_SAME_TOKEN_NAME] proposal must be approved "
          + "before [ALLOW_SVM_TRANSFER_SRC10] can be proposed", e.getMessage());
    }

    forkUtils.init(dbManager.getChainBaseManager());
    long maintenanceTimeInterval = forkUtils.getManager().getDynamicPropertiesStore()
        .getMaintenanceTimeInterval();
    long hardForkTime =
        ((ForkBlockVersionEnum.VERSION_4_0_1.getHardForkTime() - 1) / maintenanceTimeInterval + 1)
            * maintenanceTimeInterval;
    forkUtils.getManager().getDynamicPropertiesStore()
        .saveLatestBlockHeaderTimestamp(hardForkTime + 1);
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    forkUtils.getManager().getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionEnum.VERSION_4_0_1.getValue(), stats);
    ByteString address = ByteString
        .copyFrom(ByteArray.fromHexString("a0ec6525979a351a54fa09fea64beb4cce33ffbb7a"));
    List<ByteString> w = new ArrayList<>();
    w.add(address);
    forkUtils.getManager().getExecutiveScheduleStore().saveActiveExecutives(w);
    try {
      ProposalUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.ALLOW_SHIELDED_SRC20_TRANSACTION
              .getCode(), 2);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals("This value[ALLOW_SHIELDED_SRC20_TRANSACTION] is only allowed"
          + " to be 1 or 0", e.getMessage());
    }

    hardForkTime =
        ((ForkBlockVersionEnum.VERSION_4_3.getHardForkTime() - 1) / maintenanceTimeInterval + 1)
            * maintenanceTimeInterval;
    forkUtils.getManager().getDynamicPropertiesStore()
        .saveLatestBlockHeaderTimestamp(hardForkTime + 1);
    forkUtils.getManager().getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionEnum.VERSION_4_3.getValue(), stats);
    try {
      ProposalUtil.validator(dynamicPropertiesStore, forkUtils, ProposalUtil.ProposalType.FREE_NET_LIMIT
          .getCode(), -1);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals("Bad chain parameter value, valid range is [0,100_000]",
          e.getMessage());
    }

    try {
      ProposalUtil.validator(dynamicPropertiesStore, forkUtils,
          ProposalUtil.ProposalType.TOTAL_NET_LIMIT.getCode(), -1);
      Assert.fail();
    } catch (ContractValidateException e) {
      Assert.assertEquals("Bad chain parameter value, valid range is [0, 1_000_000_000_000L]",
          e.getMessage());
    }
  }
}
