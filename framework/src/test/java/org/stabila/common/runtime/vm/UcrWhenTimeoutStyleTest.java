package org.stabila.common.runtime.vm;

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
import org.stabila.common.runtime.SVMTestResult;
import org.stabila.common.runtime.SvmTestUtils;
import org.stabila.common.storage.DepositImpl;
import org.stabila.common.utils.FileUtil;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.ReceiptCheckErrException;
import org.stabila.core.exception.VMIllegalException;
import org.stabila.core.vm.program.Program.OutOfUcrException;
import org.stabila.core.vm.program.Program.OutOfTimeException;
import org.stabila.protos.Protocol.AccountType;

@Slf4j
public class UcrWhenTimeoutStyleTest {

  private Manager dbManager;
  private StabilaApplicationContext context;
  private DepositImpl deposit;
  private String dbPath = "output_CPUTimeTest";
  private String OWNER_ADDRESS;
  private Application AppT;
  private long totalBalance = 30_000_000_000_000L;


  /**
   * Init data.
   */
  @Before
  public void init() {
    Args.setParam(new String[]{"--output-directory", dbPath},
        Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    dbManager = context.getBean(Manager.class);
    deposit = DepositImpl.createRoot(dbManager);
    deposit.createAccount(Hex.decode(OWNER_ADDRESS), AccountType.Normal);
    deposit.addBalance(Hex.decode(OWNER_ADDRESS), totalBalance);
    deposit.commit();
  }

  // solidity for endlessLoopTest
  // pragma solidity ^0.4.0;
  //
  // contract TestForEndlessLoop {
  //
  //   uint256 vote;
  //   constructor () public {
  //     vote = 0;
  //   }
  //
  //   function getVote() public constant returns (uint256 _vote) {
  //     _vote = vote;
  //   }
  //
  //   function setVote(uint256 _vote) public {
  //     vote = _vote;
  //     while(true)
  //     {
  //       vote += 1;
  //     }
  //   }
  // }

  @Test
  public void endlessLoopTest()
      throws ContractExeException, ContractValidateException, ReceiptCheckErrException,
      VMIllegalException {

    long value = 0;
    long feeLimit = 15_000_000L;
    byte[] address = Hex.decode(OWNER_ADDRESS);
    long consumeUserResourcePercent = 0;
    SVMTestResult result = deployEndlessLoopContract(value, feeLimit,
        consumeUserResourcePercent);

    if (null != result.getRuntime().getResult().getException()) {
      long expectUcrUsageTotal = feeLimit / 100;
      Assert.assertEquals(result.getReceipt().getUcrUsageTotal(), expectUcrUsageTotal);
      Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
          totalBalance - expectUcrUsageTotal * 100);
      return;
    }
    long expectUcrUsageTotal = 55107;
    Assert.assertEquals(result.getReceipt().getUcrUsageTotal(), expectUcrUsageTotal);
    Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
        totalBalance - expectUcrUsageTotal * 100);

    byte[] contractAddress = result.getContractAddress();

    /* =================================== CALL setVote(uint256) =============================== */
    String params = "0000000000000000000000000000000000000000000000000000000000000003";
    byte[] triggerData = SvmTestUtils.parseAbi("setVote(uint256)", params);
    boolean haveException = false;
    result = SvmTestUtils
        .triggerContractAndReturnSvmTestResult(Hex.decode(OWNER_ADDRESS), contractAddress,
            triggerData, value, feeLimit, dbManager, null);

    long expectUcrUsageTotal2 = feeLimit / 100;
    Assert.assertEquals(result.getReceipt().getUcrUsageTotal(), expectUcrUsageTotal2);
    Exception exception = result.getRuntime().getResult().getException();
    Assert.assertTrue((exception instanceof OutOfTimeException)
        || (exception instanceof OutOfUcrException));
    Assert.assertEquals(dbManager.getAccountStore().get(address).getBalance(),
        totalBalance - (expectUcrUsageTotal + expectUcrUsageTotal2) * 100);
  }

  public SVMTestResult deployEndlessLoopContract(long value, long feeLimit,
      long consumeUserResourcePercent)
      throws ContractExeException, ReceiptCheckErrException, ContractValidateException,
      VMIllegalException {
    String contractName = "EndlessLoopContract";
    byte[] address = Hex.decode(OWNER_ADDRESS);
    String ABI = "[{\"constant\":true,\"inputs\":[],\"name\":\"getVote\",\"outputs\":[{\"name\""
        + ":\"_vote\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\","
        + "\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"_vote\",\"type\":"
        + "\"uint256\"}],\"name\":\"setVote\",\"outputs\":[],\"payable\":false,\"stateMutability\""
        + ":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"payable\":false,"
        + "\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}]";

    String code = "608060405234801561001057600080fd5b506000808190555060fa806100266000396000f3006080"
        + "604052600436106049576000357c010000000000000000000000000000000000000000000000000000000090"
        + "0463ffffffff1680630242f35114604e578063230796ae146076575b600080fd5b348015605957600080fd5b"
        + "50606060a0565b6040518082815260200191505060405180910390f35b348015608157600080fd5b50609e60"
        + "04803603810190808035906020019092919050505060a9565b005b60008054905090565b806000819055505b"
        + "60011560cb576001600080828254019250508190555060b1565b505600a165627a7a72305820290a38c9bbaf"
        + "ccaf6c7f752ab56d229e354da767efb72715ee9fdb653b9f4b6c0029";

    String libraryAddressPair = null;

    return SvmTestUtils
        .deployContractAndReturnSvmTestResult(contractName, address, ABI, code,
            value,
            feeLimit, consumeUserResourcePercent, libraryAddressPair,
            dbManager, null);
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