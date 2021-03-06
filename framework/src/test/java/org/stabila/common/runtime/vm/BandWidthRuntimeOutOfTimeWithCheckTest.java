/*
 * java-stabila is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-stabila is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.stabila.common.runtime.vm;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.runtime.RuntimeImpl;
import org.stabila.common.runtime.SvmTestUtils;
import org.stabila.common.storage.DepositImpl;
import org.stabila.common.utils.Commons;
import org.stabila.common.utils.FileUtil;
import org.stabila.core.Constant;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.db.TransactionTrace;
import org.stabila.core.exception.AccountResourceInsufficientException;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.ReceiptCheckErrException;
import org.stabila.core.exception.TooBigTransactionResultException;
import org.stabila.core.exception.StabilaException;
import org.stabila.core.exception.VMIllegalException;
import org.stabila.core.store.StoreFactory;
import org.stabila.protos.Protocol.AccountType;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;
import org.stabila.protos.Protocol.Transaction.Result.contractResult;
import org.stabila.protos.Protocol.Transaction.raw;
import org.stabila.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.TriggerSmartContract;

/**
 * pragma solidity ^0.4.2;
 *
 * contract Fibonacci {
 *
 * event Notify(uint input, uint result);
 *
 * function fibonacci(uint number) constant returns(uint result) { if (number == 0) { return 0; }
 * else if (number == 1) { return 1; } else { uint256 first = 0; uint256 second = 1; uint256 ret =
 * 0; for(uint256 i = 2; i <= number; i++) { ret = first + second; first = second; second = ret; }
 * return ret; } }
 *
 * function fibonacciNotify(uint number) returns(uint result) { result = fibonacci(number);
 * Notify(number, result); } }
 */
public class BandWidthRuntimeOutOfTimeWithCheckTest {

  public static final long totalBalance = 1000_0000_000_000L;
  private static String dbPath = "output_BandWidthRuntimeOutOfTimeTest_test";
  private static String dbDirectory = "db_BandWidthRuntimeOutOfTimeTest_test";
  private static String indexDirectory = "index_BandWidthRuntimeOutOfTimeTest_test";
  private static AnnotationConfigApplicationContext context;
  private static Manager dbManager;

  private static String OwnerAddress = "SX7mkf6BWiWp6ieDEWUMjTo9DzKg6vnCMH";
  private static String TriggerOwnerAddress = "SRft1y3PUGqKzZPs1D8gih4ksFWV6AMAjo";

  static {
    Args.setParam(
        new String[]{
            "--output-directory", dbPath,
            "--storage-db-directory", dbDirectory,
            "--storage-index-directory", indexDirectory,
            "-w"
        },
        "config-test-mainnet.conf"
    );
    context = new StabilaApplicationContext(DefaultConfig.class);
  }

  private String stb2ContractAddress = "TPMBUANrTwwQAPwShn7ZZjTJz1f3F8jknj";

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    //init ucr
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647837000L);
    dbManager.getDynamicPropertiesStore().saveTotalUcrWeight(10_000_000L);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(0);

    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(OwnerAddress)), AccountType.Normal,
        totalBalance);

    accountCapsule.setCdedForUcr(10_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(OwnerAddress), accountCapsule);

    AccountCapsule accountCapsule2 = new AccountCapsule(ByteString.copyFrom("owner".getBytes()),
        ByteString.copyFrom(Commons.decodeFromBase58Check(TriggerOwnerAddress)), AccountType.Normal,
        totalBalance);

    accountCapsule2.setCdedForUcr(10_000_000L, 0L);
    dbManager.getAccountStore()
        .put(Commons.decodeFromBase58Check(TriggerOwnerAddress), accountCapsule2);
    dbManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderTimestamp(System.currentTimeMillis() / 1000);
  }

  /**
   * destroy clear data of testing.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void testSuccess() {
    try {
      byte[] contractAddress = createContract();
      AccountCapsule triggerOwner = dbManager.getAccountStore()
          .get(Commons.decodeFromBase58Check(TriggerOwnerAddress));
      long ucr = triggerOwner.getUcrUsage();
      long balance = triggerOwner.getBalance();
      TriggerSmartContract triggerContract = SvmTestUtils.createTriggerContract(contractAddress,
          "fibonacciNotify(uint256)", "100001", false,
          0, Commons.decodeFromBase58Check(TriggerOwnerAddress));
      Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
          Contract.newBuilder().setParameter(Any.pack(triggerContract))
              .setType(ContractType.TriggerSmartContract)).setFeeLimit(1000000000)).build();
      TransactionCapsule stbCap = new TransactionCapsule(transaction);
      stbCap.setResultCode(contractResult.OUT_OF_UCR);
      TransactionTrace trace = new TransactionTrace(stbCap, StoreFactory.getInstance(),
          new RuntimeImpl());
      dbManager.consumeBandwidth(stbCap, trace);
      BlockCapsule blockCapsule = null;
      trace.init(blockCapsule);
      trace.exec();
      trace.finalization();
      trace.check();
      triggerOwner = dbManager.getAccountStore()
          .get(Commons.decodeFromBase58Check(TriggerOwnerAddress));
      ucr = triggerOwner.getUcrUsage() - ucr;
      balance = balance - triggerOwner.getBalance();
      Assert.assertNotNull(trace.getRuntimeError());
      Assert.assertTrue(trace.getRuntimeError().contains(" timeout "));
      Assert.assertEquals(9950000, trace.getReceipt().getUcrUsageTotal());
      Assert.assertEquals(50000, ucr);
      Assert.assertEquals(990000000, balance);
      Assert.assertEquals(9950000 * Constant.UNIT_PER_UCR,
          balance + ucr * Constant.UNIT_PER_UCR);
    } catch (StabilaException e) {
      Assert.assertNotNull(e);
    } catch (ReceiptCheckErrException e) {
      Assert.assertNotNull(e);
    }
  }

  private byte[] createContract()
      throws ContractValidateException, AccountResourceInsufficientException,
      TooBigTransactionResultException, ContractExeException, VMIllegalException {
    AccountCapsule owner = dbManager.getAccountStore()
        .get(Commons.decodeFromBase58Check(OwnerAddress));
    long ucr = owner.getUcrUsage();
    long balance = owner.getBalance();

    String contractName = "Fibonacci";
    String code = "608060405234801561001057600080fd5b506101ba806100206000396000f3006080604052600"
        + "4361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463"
        + "ffffffff1680633c7fdc701461005157806361047ff414610092575b600080fd5b34801561005d5760008"
        + "0fd5b5061007c600480360381019080803590602001909291905050506100d3565b604051808281526020"
        + "0191505060405180910390f35b34801561009e57600080fd5b506100bd600480360381019080803590602"
        + "00190929190505050610124565b6040518082815260200191505060405180910390f35b60006100de8261"
        + "0124565b90507f71e71a8458267085d5ab16980fd5f114d2d37f232479c245d523ce8d23ca40ed8282604"
        + "051808381526020018281526020019250505060405180910390a1919050565b6000806000806000808614"
        + "1561013d5760009450610185565b600186141561014f5760019450610185565b600093506001925060009"
        + "150600290505b85811115156101815782840191508293508192508080600101915050610160565b819450"
        + "5b505050509190505600a165627a7a7230582071f3cf655137ce9dc32d3307fb879e65f3960769282e6e4"
        + "52a5f0023ea046ed20029";

    String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"number\",\"type\":\"uint256\"}],"
        + "\"name\":\"fibonacciNotify\",\"outputs\":[{\"name\":\"result\",\"type\":\"uint256\"}],"
        + "\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},"
        + "{\"constant\":true,\"inputs\":[{\"name\":\"number\",\"type\":\"uint256\"}],"
        + "\"name\":\"fibonacci\",\"outputs\":[{\"name\":\"result\",\"type\":\"uint256\"}],"
        + "\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\""
        + ":false,\"inputs\":[{\"indexed\":false,\"name\":\"input\",\"type\":\"uint256\"},"
        + "{\"indexed\":false,\"name\":\"result\",\"type\":\"uint256\"}],\"name\":\"Notify\","
        + "\"type\":\"event\"}]";

    CreateSmartContract smartContract = SvmTestUtils.createSmartContract(
        Commons.decodeFromBase58Check(OwnerAddress), contractName, abi, code,
        0, 100);
    Transaction transaction = Transaction.newBuilder().setRawData(raw.newBuilder().addContract(
        Contract.newBuilder().setParameter(Any.pack(smartContract))
            .setType(ContractType.CreateSmartContract)).setFeeLimit(1000000000)).build();
    TransactionCapsule stbCap = new TransactionCapsule(transaction);
    TransactionTrace trace = new TransactionTrace(stbCap, StoreFactory.getInstance(),
        new RuntimeImpl());
    dbManager.consumeBandwidth(stbCap, trace);
    BlockCapsule blockCapsule = null;
    DepositImpl deposit = DepositImpl.createRoot(dbManager);
    trace.init(blockCapsule);
    trace.exec();
    trace.finalization();
    owner = dbManager.getAccountStore()
        .get(Commons.decodeFromBase58Check(OwnerAddress));
    ucr = owner.getUcrUsage() - ucr;
    balance = balance - owner.getBalance();
    Assert.assertEquals(88529, trace.getReceipt().getUcrUsageTotal());
    Assert.assertEquals(50000, ucr);
    Assert.assertEquals(3852900, balance);
    Assert.assertEquals(88529 * 100, balance + ucr * 100);
    if (trace.getRuntimeError() != null) {
      return trace.getRuntimeResult().getContractAddress();
    }
    return trace.getRuntimeResult().getContractAddress();
  }
}