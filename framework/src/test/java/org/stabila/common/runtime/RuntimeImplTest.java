package org.stabila.common.runtime;

import static org.stabila.common.runtime.SvmTestUtils.generateDeploySmartContractAndGetTransaction;
import static org.stabila.common.runtime.SvmTestUtils.generateTriggerSmartContractAndGetTransaction;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testng.Assert;
import org.stabila.common.application.Application;
import org.stabila.common.application.ApplicationFactory;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.utils.FileUtil;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.actuator.VMActuator;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.ContractCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.db.TransactionContext;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.ReceiptCheckErrException;
import org.stabila.core.exception.VMIllegalException;
import org.stabila.core.store.StoreFactory;
import org.stabila.core.vm.repository.Repository;
import org.stabila.core.vm.repository.RepositoryImpl;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.contract.SmartContractOuterClass.TriggerSmartContract;


@Slf4j

public class RuntimeImplTest {

  private Manager dbManager;
  private StabilaApplicationContext context;
  private Repository repository;
  private String dbPath = "output_RuntimeImplTest";
  private Application AppT;
  private byte[] callerAddress;
  private long callerTotalBalance = 4_000_000_000L;
  private byte[] creatorAddress;
  private long creatorTotalBalance = 3_000_000_000L;

  /**
   * Init data.
   */
  @Before
  public void init() {
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
    callerAddress = Hex
        .decode(Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc");
    creatorAddress = Hex
        .decode(Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abd");
    dbManager = context.getBean(Manager.class);
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    dbManager.getDynamicPropertiesStore().saveTotalUcrWeight(5_000_000_000L); // unit is stb
    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    repository.createAccount(callerAddress, AccountType.Normal);
    repository.addBalance(callerAddress, callerTotalBalance);
    repository.createAccount(creatorAddress, AccountType.Normal);
    repository.addBalance(creatorAddress, creatorTotalBalance);
    repository.commit();
  }

  // // solidity src code
  // pragma solidity ^0.4.2;
  //
  // contract TestUcrLimit {
  //
  //   function testNotConstant(uint256 count) {
  //     uint256 curCount = 0;
  //     while(curCount < count) {
  //       uint256 a = 1;
  //       curCount += 1;
  //     }
  //   }
  //
  //   function testConstant(uint256 count) constant {
  //     uint256 curCount = 0;
  //     while(curCount < count) {
  //       uint256 a = 1;
  //       curCount += 1;
  //     }
  //   }
  //
  // }


  @Test
  public void getCreatorUcrLimit2Test() throws ContractValidateException, ContractExeException {

    long value = 10L;
    long feeLimit = 15_000_000L;
    long consumeUserResourcePercent = 0L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],\""
        + "name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"view"
        + "\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\",\"type"
        + "\":\"uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\":false,\""
        + "stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b506076600"
        + "4803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a0600480"
        + "3603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf576001905"
        + "060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc56"
        + "5b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce25765"
        + "4c8c17b0029";
    String libraryAddressPair = null;

    Transaction stb = generateDeploySmartContractAndGetTransaction(contractName, creatorAddress,
        ABI,
        code, value, feeLimit, consumeUserResourcePercent, libraryAddressPair);

    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(stb),
            StoreFactory.getInstance(), true, true));

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);

    long expectUcrLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountUcrLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectUcrLimit1);

    value = 2_500_000_000L;
    long expectUcrLimit2 = 5_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountUcrLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectUcrLimit2);

    value = 10L;
    feeLimit = 1_000_000L;
    long expectUcrLimit3 = 10_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountUcrLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectUcrLimit3);

    long cdedBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - cdedBalance;
    creatorAccount.setCdedForUcr(cdedBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 15_000_000L;
    long expectUcrLimit4 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountUcrLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectUcrLimit4);

    feeLimit = 3_000_000_000L;
    value = 10L;
    long expectUcrLimit5 = 20_009_999L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountUcrLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectUcrLimit5);

    feeLimit = 3_000L;
    value = 10L;
    long expectUcrLimit6 = 30L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getAccountUcrLimitWithFixRatio(creatorAccount, feeLimit, value),
        expectUcrLimit6);

  }

  @Test
  public void getCallerAndCreatorUcrLimit2With0PercentTest()
      throws ContractExeException, ReceiptCheckErrException, VMIllegalException,
      ContractValidateException {

    long value = 0;
    long feeLimit = 15_000_000L; // unit
    long consumeUserResourcePercent = 0L;
    long creatorUcrLimit = 5_000L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],"
        + "\"name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\""
        + "view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\","
        + "\"type\":\"uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\""
        + ":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b506076600"
        + "4803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a0600480"
        + "3603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf576001905"
        + "060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc56"
        + "5b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce25765"
        + "4c8c17b0029";
    String libraryAddressPair = null;
    SVMTestResult result = SvmTestUtils
        .deployContractWithCreatorUcrLimitAndReturnSvmTestResult(contractName, creatorAddress,
            ABI, code, value,
            feeLimit, consumeUserResourcePercent, libraryAddressPair, dbManager, null,
            creatorUcrLimit);

    byte[] contractAddress = result.getContractAddress();
    byte[] triggerData = SvmTestUtils.parseAbi("testNotConstant()", null);
    Transaction stb = generateTriggerSmartContractAndGetTransaction(callerAddress, contractAddress,
        triggerData, value, feeLimit);

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(stb),
            StoreFactory.getInstance(), true, true));

    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);
    AccountCapsule callerAccount = repository.getAccount(callerAddress);
    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(stb);

    feeLimit = 15_000_000L;
    value = 0L;
    long expectUcrLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit1);

    long creatorCdedBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - creatorCdedBalance;
    creatorAccount.setCdedForUcr(creatorCdedBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 15_000_000L;
    value = 0L;
    long expectUcrLimit2 = 10_005_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit2);

    value = 3_500_000_000L;
    long expectUcrLimit3 = 5_005_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit3);

    value = 10L;
    feeLimit = 5_000_000_000L;
    long expectUcrLimit4 = 40_004_999L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit4);

    long callerCdedBalance = 1_000_000_000L;
    callerAccount.setCdedForUcr(callerCdedBalance, 0L);
    callerAccount.setBalance(callerAccount.getBalance() - callerCdedBalance);
    repository.putAccountValue(callerAddress, callerAccount);
    repository.commit();

    value = 10L;
    feeLimit = 5_000_000_000L;
    long expectUcrLimit5 = 30_014_999L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit5);

  }

  @Test
  public void getCallerAndCreatorUcrLimit2With40PercentTest()
      throws ContractExeException, ReceiptCheckErrException, VMIllegalException,
      ContractValidateException {

    long value = 0;
    long feeLimit = 15_000_000L; // unit
    long consumeUserResourcePercent = 40L;
    long creatorUcrLimit = 5_000L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],\""
        + "name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"view\""
        + ",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\",\"type\":"
        + "\"uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\":false,\""
        + "stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b50607660"
        + "04803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a060048"
        + "03603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf57600190"
        + "5060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc5"
        + "65b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce2576"
        + "54c8c17b0029";
    String libraryAddressPair = null;
    SVMTestResult result = SvmTestUtils
        .deployContractWithCreatorUcrLimitAndReturnSvmTestResult(contractName, creatorAddress,
            ABI, code, value,
            feeLimit, consumeUserResourcePercent, libraryAddressPair, dbManager, null,
            creatorUcrLimit);

    byte[] contractAddress = result.getContractAddress();
    byte[] triggerData = SvmTestUtils.parseAbi("testNotConstant()", null);
    Transaction stb = generateTriggerSmartContractAndGetTransaction(callerAddress, contractAddress,
        triggerData, value, feeLimit);

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(stb),
            StoreFactory.getInstance(), true, true));

    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);
    AccountCapsule callerAccount = repository.getAccount(callerAddress);
    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(stb);

    feeLimit = 15_000_000L;
    value = 0L;
    long expectUcrLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit1);

    long creatorCdedBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - creatorCdedBalance;
    creatorAccount.setCdedForUcr(creatorCdedBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 15_000_000L;
    value = 0L;
    long expectUcrLimit2 = 10_005_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit2);

    value = 3_999_950_000L;
    long expectUcrLimit3 = 1_250L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit3);

  }

  @Test
  public void getCallerAndCreatorUcrLimit2With100PercentTest()
      throws ContractExeException, ReceiptCheckErrException, VMIllegalException,
      ContractValidateException {

    long value = 0;
    long feeLimit = 15_000_000L; // unit
    long consumeUserResourcePercent = 100L;
    long creatorUcrLimit = 5_000L;
    String contractName = "test";
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],"
        + "\"name\":\"testConstant\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"view\""
        + ",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"count\",\"type\":\""
        + "uint256\"}],\"name\":\"testNotConstant\",\"outputs\":[],\"payable\":false,\""
        + "stateMutability\":\"nonpayable\",\"type\":\"function\"}]";
    String code = "608060405234801561001057600080fd5b50610112806100206000396000f300608060405260043"
        + "6106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffff"
        + "ff16806321964a3914604e5780634c6bb6eb146078575b600080fd5b348015605957600080fd5b506076600"
        + "4803603810190808035906020019092919050505060a2565b005b348015608357600080fd5b5060a0600480"
        + "3603810190808035906020019092919050505060c4565b005b600080600091505b8282101560bf576001905"
        + "060018201915060aa565b505050565b600080600091505b8282101560e1576001905060018201915060cc56"
        + "5b5050505600a165627a7a72305820267cf0ebf31051a92ff62bed7490045b8063be9f1e1a22d07dce25765"
        + "4c8c17b0029";
    String libraryAddressPair = null;
    SVMTestResult result = SvmTestUtils
        .deployContractWithCreatorUcrLimitAndReturnSvmTestResult(contractName, creatorAddress,
            ABI, code, value,
            feeLimit, consumeUserResourcePercent, libraryAddressPair, dbManager, null,
            creatorUcrLimit);

    byte[] contractAddress = result.getContractAddress();
    byte[] triggerData = SvmTestUtils.parseAbi("testNotConstant()", null);
    Transaction stb = generateTriggerSmartContractAndGetTransaction(callerAddress, contractAddress,
        triggerData, value, feeLimit);

    repository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    RuntimeImpl runtimeImpl = new RuntimeImpl();
    runtimeImpl.execute(
        new TransactionContext(null, new TransactionCapsule(stb),
            StoreFactory.getInstance(), true, true));

    AccountCapsule creatorAccount = repository.getAccount(creatorAddress);
    AccountCapsule callerAccount = repository.getAccount(callerAddress);
    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(stb);

    feeLimit = 15_000_000L;
    value = 0L;
    long expectUcrLimit1 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit1);

    long creatorCdedBalance = 1_000_000_000L;
    long newBalance = creatorAccount.getBalance() - creatorCdedBalance;
    creatorAccount.setCdedForUcr(creatorCdedBalance, 0L);
    creatorAccount.setBalance(newBalance);
    repository.putAccountValue(creatorAddress, creatorAccount);
    repository.commit();

    feeLimit = 15_000_000L;
    value = 0L;
    long expectUcrLimit2 = 10_000_000L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit2);

    value = 3_999_950_000L;
    long expectUcrLimit3 = 500L;
    Assert.assertEquals(
        ((VMActuator) runtimeImpl.getActuator2())
            .getTotalUcrLimitWithFixRatio(creatorAccount, callerAccount, contract, feeLimit,
                value),
        expectUcrLimit3);

  }

  /**
   * Release resources.
   */
  @After
  public void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }
}

