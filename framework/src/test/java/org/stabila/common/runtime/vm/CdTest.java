package org.stabila.common.runtime.vm;

import static org.stabila.core.config.Parameter.ChainConstant.CDED_PERIOD;
import static org.stabila.core.config.Parameter.ChainConstant.STB_PRECISION;
import static org.stabila.protos.Protocol.Transaction.Result.contractResult.REVERT;
import static org.stabila.protos.Protocol.Transaction.Result.contractResult.SUCCESS;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stabila.common.application.StabilaApplicationContext;
import org.stabila.common.runtime.Runtime;
import org.stabila.common.runtime.RuntimeImpl;
import org.stabila.common.runtime.SVMTestResult;
import org.stabila.common.runtime.SvmTestUtils;
import org.stabila.common.storage.Deposit;
import org.stabila.common.storage.DepositImpl;
import org.stabila.common.utils.Commons;
import org.stabila.common.utils.FastByteComparisons;
import org.stabila.common.utils.FileUtil;
import org.stabila.common.utils.StringUtil;
import org.stabila.common.utils.WalletUtil;
import org.stabila.core.Constant;
import org.stabila.core.Wallet;
import org.stabila.core.capsule.AccountCapsule;
import org.stabila.core.capsule.DelegatedResourceCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.config.DefaultConfig;
import org.stabila.core.config.args.Args;
import org.stabila.core.db.Manager;
import org.stabila.core.db.TransactionTrace;
import org.stabila.core.store.AccountStore;
import org.stabila.core.store.DelegatedResourceStore;
import org.stabila.core.store.DynamicPropertiesStore;
import org.stabila.core.store.StoreFactory;
import org.stabila.core.vm.UcrCost;
import org.stabila.core.vm.config.ConfigLoader;
import org.stabila.core.vm.config.VMConfig;
import org.stabila.protos.Protocol;
import org.stabila.protos.Protocol.Transaction.Result.contractResult;
import stest.stabila.wallet.common.client.utils.AbiUtil;

@Slf4j
public class CdTest {

  private static final String CONTRACT_CODE = "608060405261037e806100136000396000f3fe6080604052"
      + "34801561001057600080fd5b50d3801561001d57600080fd5b50d2801561002a57600080fd5b506004361061"
      + "00655760003560e01c8062f55d9d1461006a57806330e1e4e5146100ae5780637b46b80b1461011a578063e7"
      + "aa4e0b1461017c575b600080fd5b6100ac6004803603602081101561008057600080fd5b81019080803573ff"
      + "ffffffffffffffffffffffffffffffffffffff1690602001909291905050506101de565b005b610104600480"
      + "360360608110156100c457600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff16"
      + "906020019092919080359060200190929190803590602001909291905050506101f7565b6040518082815260"
      + "200191505060405180910390f35b6101666004803603604081101561013057600080fd5b81019080803573ff"
      + "ffffffffffffffffffffffffffffffffffffff169060200190929190803590602001909291905050506102f0"
      + "565b6040518082815260200191505060405180910390f35b6101c86004803603604081101561019257600080"
      + "fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff169060200190929190803590602001"
      + "90929190505050610327565b6040518082815260200191505060405180910390f35b8073ffffffffffffffff"
      + "ffffffffffffffffffffffff16ff5b60008373ffffffffffffffffffffffffffffffffffffffff168383d515"
      + "8015610224573d6000803e3d6000fd5b50423073ffffffffffffffffffffffffffffffffffffffff1663e7aa"
      + "4e0b86856040518363ffffffff1660e01b8152600401808373ffffffffffffffffffffffffffffffffffffff"
      + "ff1673ffffffffffffffffffffffffffffffffffffffff168152602001828152602001925050506020604051"
      + "8083038186803b1580156102ab57600080fd5b505afa1580156102bf573d6000803e3d6000fd5b5050505060"
      + "40513d60208110156102d557600080fd5b81019080805190602001909291905050500390509392505050565b"
      + "60008273ffffffffffffffffffffffffffffffffffffffff1682d615801561031c573d6000803e3d6000fd5b"
      + "506001905092915050565b60008273ffffffffffffffffffffffffffffffffffffffff1682d7905092915050"
      + "56fea26474726f6e58200fd975eab4a8c8afe73bf3841efe4da7832d5a0d09f07115bb695c7260ea64216473"
      + "6f6c63430005100031";
  private static final String FACTORY_CODE = "6080604052610640806100136000396000f3fe60806040523"
      + "4801561001057600080fd5b50d3801561001d57600080fd5b50d2801561002a57600080fd5b5060043610610"
      + "0505760003560e01c806341aa901414610055578063bb63e785146100c3575b600080fd5b610081600480360"
      + "3602081101561006b57600080fd5b8101908080359060200190929190505050610131565b604051808273fff"
      + "fffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526"
      + "0200191505060405180910390f35b6100ef600480360360208110156100d957600080fd5b810190808035906"
      + "020019092919050505061017d565b604051808273ffffffffffffffffffffffffffffffffffffffff1673fff"
      + "fffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b600080606060405"
      + "1806020016101469061026e565b6020820181038252601f19601f82011660405250905083815160208301600"
      + "0f59150813b61017357600080fd5b8192505050919050565b60008060a060f81b30846040518060200161019"
      + "79061026e565b6020820181038252601f19601f820116604052508051906020012060405160200180857efff"
      + "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffff19167efffffffffffffffffffffff"
      + "fffffffffffffffffffffffffffffffffffffff191681526001018473fffffffffffffffffffffffffffffff"
      + "fffffffff1673ffffffffffffffffffffffffffffffffffffffff1660601b815260140183815260200182815"
      + "26020019450505050506040516020818303038152906040528051906020012060001c9050809150509190505"
      + "65b6103918061027c8339019056fe608060405261037e806100136000396000f3fe608060405234801561001"
      + "057600080fd5b50d3801561001d57600080fd5b50d2801561002a57600080fd5b50600436106100655760003"
      + "560e01c8062f55d9d1461006a57806330e1e4e5146100ae5780637b46b80b1461011a578063e7aa4e0b14610"
      + "17c575b600080fd5b6100ac6004803603602081101561008057600080fd5b81019080803573fffffffffffff"
      + "fffffffffffffffffffffffffff1690602001909291905050506101de565b005b61010460048036036060811"
      + "0156100c457600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff1690602001909"
      + "2919080359060200190929190803590602001909291905050506101f7565b604051808281526020019150506"
      + "0405180910390f35b6101666004803603604081101561013057600080fd5b81019080803573fffffffffffff"
      + "fffffffffffffffffffffffffff169060200190929190803590602001909291905050506102f0565b6040518"
      + "082815260200191505060405180910390f35b6101c86004803603604081101561019257600080fd5b8101908"
      + "0803573ffffffffffffffffffffffffffffffffffffffff16906020019092919080359060200190929190505"
      + "050610327565b6040518082815260200191505060405180910390f35b8073fffffffffffffffffffffffffff"
      + "fffffffffffff16ff5b60008373ffffffffffffffffffffffffffffffffffffffff168383d51580156102245"
      + "73d6000803e3d6000fd5b50423073ffffffffffffffffffffffffffffffffffffffff1663e7aa4e0b8685604"
      + "0518363ffffffff1660e01b8152600401808373ffffffffffffffffffffffffffffffffffffffff1673fffff"
      + "fffffffffffffffffffffffffffffffffff16815260200182815260200192505050602060405180830381868"
      + "03b1580156102ab57600080fd5b505afa1580156102bf573d6000803e3d6000fd5b505050506040513d60208"
      + "110156102d557600080fd5b81019080805190602001909291905050500390509392505050565b60008273fff"
      + "fffffffffffffffffffffffffffffffffffff1682d615801561031c573d6000803e3d6000fd5b50600190509"
      + "2915050565b60008273ffffffffffffffffffffffffffffffffffffffff1682d790509291505056fea264747"
      + "26f6e58200fd975eab4a8c8afe73bf3841efe4da7832d5a0d09f07115bb695c7260ea642164736f6c6343000"
      + "5100031a26474726f6e5820403c4e856a1ab2fe0eeaf6b157c29c07fef7a9e9bdc6f0faac870d2d8873159d6"
      + "4736f6c63430005100031";

  private static final long value = 100_000_000_000_000_000L;
  private static final long fee = 10_000_000;
  private static final String userAStr = "27k66nycZATHzBasFT9782nTsYWqVtxdtAc";
  private static final byte[] userA = Commons.decode58Check(userAStr);
  private static final String userBStr = "27jzp7nVEkH4Hf3H1PHPp4VDY7DxTy5eydL";
  private static final byte[] userB = Commons.decode58Check(userBStr);
  private static final String userCStr = "27juXSbMvL6pb8VgmKRgW6ByCfw5RqZjUuo";
  private static final byte[] userC = Commons.decode58Check(userCStr);

  private static String dbPath;
  private static StabilaApplicationContext context;
  private static Manager manager;
  private static byte[] owner;
  private static Deposit rootDeposit;

  @Before
  public void init() throws Exception {
    dbPath = "output_" + CdTest.class.getName();
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
    context = new StabilaApplicationContext(DefaultConfig.class);
    manager = context.getBean(Manager.class);
    owner = Hex.decode(Wallet.getAddressPreFixString()
        + "abd4b9367799eaa3197fecb144eb71de1e049abc");
    rootDeposit = DepositImpl.createRoot(manager);
    rootDeposit.createAccount(owner, Protocol.AccountType.Normal);
    rootDeposit.addBalance(owner, 900_000_000_000_000_000L);
    rootDeposit.commit();

    ConfigLoader.disable = true;
    manager.getDynamicPropertiesStore().saveAllowSvmCd(1);
    VMConfig.initVmHardFork(true);
    VMConfig.initAllowSvmTransferSrc10(1);
    VMConfig.initAllowSvmConstantinople(1);
    VMConfig.initAllowSvmSolidity059(1);
    VMConfig.initAllowSvmIstanbul(1);
    VMConfig.initAllowSvmCd(1);
  }

  private byte[] deployContract(String contractName, String code) throws Exception {
    return deployContract(owner, contractName, code, 0, 100_000);
  }

  private byte[] deployContract(byte[] deployer,
                                String contractName,
                                String code,
                                long consumeUserResourcePercent,
                                long originUcrLimit) throws Exception {
    Protocol.Transaction stb = SvmTestUtils.generateDeploySmartContractAndGetTransaction(
        contractName, deployer, "[]", code, value, fee, consumeUserResourcePercent,
        null, originUcrLimit);
    byte[] contractAddr = WalletUtil.generateContractAddress(stb);
    //String contractAddrStr = StringUtil.encode58Check(contractAddr);
    TransactionCapsule stbCap = new TransactionCapsule(stb);
    TransactionTrace trace = new TransactionTrace(stbCap, StoreFactory.getInstance(),
        new RuntimeImpl());
    stbCap.setStbTrace(trace);
    trace.init(null);
    trace.exec();
    trace.finalization();
    Runtime runtime = trace.getRuntime();
    Assert.assertEquals(SUCCESS, runtime.getResult().getResultCode());
    Assert.assertEquals(value, manager.getAccountStore().get(contractAddr).getBalance());

    return contractAddr;
  }

  private SVMTestResult triggerContract(byte[] callerAddr,
                                        byte[] contractAddr,
                                        long feeLimit,
                                        contractResult expectedResult,
                                        Consumer<byte[]> check,
                                        String method,
                                        Object... args) throws Exception {
    String hexInput = AbiUtil.parseMethod(method, Arrays.asList(args));
    TransactionCapsule stbCap = new TransactionCapsule(
        SvmTestUtils.generateTriggerSmartContractAndGetTransaction(
            callerAddr, contractAddr, Hex.decode(hexInput), 0, feeLimit));
    TransactionTrace trace = new TransactionTrace(stbCap, StoreFactory.getInstance(),
        new RuntimeImpl());
    stbCap.setStbTrace(trace);
    trace.init(null);
    trace.exec();
    trace.finalization();
    trace.setResult();
    SVMTestResult result = new SVMTestResult(trace.getRuntime(), trace.getReceipt(), null);
    Assert.assertEquals(expectedResult, result.getReceipt().getResult());
    if (check != null) {
      check.accept(result.getRuntime().getResult().getHReturn());
    }
    return result;
  }

  private SVMTestResult triggerCd(byte[] callerAddr,
                                      byte[] contractAddr,
                                      byte[] receiverAddr,
                                      long cdedBalance,
                                      long res,
                                      contractResult expectedResult,
                                      Consumer<byte[]> check) throws Exception {
    return triggerContract(callerAddr, contractAddr, fee, expectedResult, check,
        "cd(address,uint256,uint256)", StringUtil.encode58Check(receiverAddr), cdedBalance,
        res);
  }

  private SVMTestResult triggerUncd(byte[] callerAddr,
                                        byte[] contractAddr,
                                        byte[] receiverAddr,
                                        long res,
                                        contractResult expectedResult,
                                        Consumer<byte[]> check) throws Exception {
    return triggerContract(callerAddr, contractAddr, fee, expectedResult, check,
        "uncd(address,uint256)", StringUtil.encode58Check(receiverAddr), res);
  }

  private SVMTestResult triggerSuicide(byte[] callerAddr,
                                       byte[] contractAddr,
                                       byte[] inheritorAddr,
                                       contractResult expectedResult,
                                       Consumer<byte[]> check) throws Exception {
    return triggerContract(callerAddr, contractAddr, fee, expectedResult, check,
        "destroy(address)", StringUtil.encode58Check(inheritorAddr));
  }

  private void setBalance(byte[] accountAddr,
                          long balance) {
    AccountCapsule accountCapsule = manager.getAccountStore().get(accountAddr);
    if (accountCapsule == null) {
      accountCapsule = new AccountCapsule(ByteString.copyFrom(accountAddr),
          Protocol.AccountType.Normal);
    }
    accountCapsule.setBalance(balance);
    manager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
  }

  private void setCdedForUcr(byte[] accountAddr, long cdedBalance) {
    AccountCapsule accountCapsule = manager.getAccountStore().get(accountAddr);
    if (accountCapsule == null) {
      accountCapsule = new AccountCapsule(ByteString.copyFrom(accountAddr),
          Protocol.AccountType.Normal);
    }
    accountCapsule.setCdedForUcr(cdedBalance, 0);
    manager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
  }

  private byte[] getCreate2Addr(byte[] factoryAddr,
                                long salt) throws Exception {
    SVMTestResult result = triggerContract(
        owner, factoryAddr, fee, SUCCESS, null, "getCreate2Addr(uint256)", salt);
    return TransactionTrace.convertToStabilaAddress(
        new DataWord(result.getRuntime().getResult().getHReturn()).getLast20Bytes());
  }

  private byte[] deployCreate2Contract(byte[] factoryAddr,
                                       long salt) throws Exception {
    SVMTestResult result = triggerContract(
        owner, factoryAddr, fee, SUCCESS, null, "deployCreate2Contract(uint256)", salt);
    return TransactionTrace.convertToStabilaAddress(
        new DataWord(result.getRuntime().getResult().getHReturn()).getLast20Bytes());
  }

  @Test
  public void testWithCallerUcrChangedInTx() throws Exception {
    byte[] contractAddr = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 10_000_000;
    AccountStore accountStore = manager.getAccountStore();
    AccountCapsule account = new AccountCapsule(ByteString.copyFromUtf8("Yang"),
        ByteString.copyFrom(userA), Protocol.AccountType.Normal, 10_000_000);
    account.setCdedForUcr(10_000_000, 1);
    accountStore.put(account.createDbKey(), account);
    manager.getDynamicPropertiesStore().addTotalUcrWeight(10);

    SVMTestResult result = cdForOther(userA, contractAddr, userA, cdedBalance, 1);

    System.out.println(result.getReceipt().getUcrUsageTotal());
    System.out.println(accountStore.get(userA));
    System.out.println(accountStore.get(owner));

    clearDelegatedExpireTime(contractAddr, userA);

    result = uncdForOther(userA, contractAddr, userA, 1);

    System.out.println(result.getReceipt().getUcrUsageTotal());
    System.out.println(accountStore.get(userA));
    System.out.println(accountStore.get(owner));
  }

  @Test
  public void testCdAndUncd() throws Exception {
    byte[] contract = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;

    // trigger cdForSelf(uint256,uint256) to get bandwidth
    cdForSelf(contract, cdedBalance, 0);

    // trigger cdForSelf(uint256,uint256) to get ucr
    cdForSelf(contract, cdedBalance, 1);

    // tests of cdForSelf(uint256,uint256) with invalid args
    cdForSelfWithException(contract, cdedBalance, 2);
    cdForSelfWithException(contract, 0, 0);
    cdForSelfWithException(contract, -cdedBalance, 0);
    cdForSelfWithException(contract, cdedBalance - 1, 1);
    cdForSelfWithException(contract, value, 0);

    // not time to uncd
    uncdForSelfWithException(contract, 0);
    uncdForSelfWithException(contract, 1);
    // invalid args
    uncdForSelfWithException(contract, 2);

    clearExpireTime(contract);

    uncdForSelfWithException(contract, 2);
    uncdForSelf(contract, 0);
    uncdForSelf(contract, 1);
    uncdForSelfWithException(contract, 0);
    uncdForSelfWithException(contract, 1);

    long ucrWithCreatingAccountA = cdForOther(contract, userA, cdedBalance, 0)
        .getReceipt().getUcrUsageTotal();

    long ucrWithoutCreatingAccountA = cdForOther(contract, userA, cdedBalance, 0)
        .getReceipt().getUcrUsageTotal();
    Assert.assertEquals(ucrWithCreatingAccountA - UcrCost.getInstance().getNewAcctCall(),
        ucrWithoutCreatingAccountA);

    cdForOther(contract, userA, cdedBalance, 1);

    long ucrWithCreatingAccountB = cdForOther(contract, userB, cdedBalance, 1)
        .getReceipt().getUcrUsageTotal();

    long ucrWithoutCreatingAccountB = cdForOther(contract, userB, cdedBalance, 1)
        .getReceipt().getUcrUsageTotal();
    Assert.assertEquals(ucrWithCreatingAccountB - UcrCost.getInstance().getNewAcctCall(),
        ucrWithoutCreatingAccountB);

    cdForOther(contract, userB, cdedBalance, 0);

    cdForOtherWithException(contract, userC, cdedBalance, 2);
    cdForOtherWithException(contract, userC, 0, 0);
    cdForOtherWithException(contract, userB, -cdedBalance, 0);
    cdForOtherWithException(contract, userC, cdedBalance - 1, 1);
    cdForOtherWithException(contract, userB, value, 0);
    cdForOtherWithException(contract,
        deployContract("OtherContract", CONTRACT_CODE), cdedBalance, 0);

    uncdForOtherWithException(contract, userA, 0);
    uncdForOtherWithException(contract, userA, 1);
    uncdForOtherWithException(contract, userA, 2);
    uncdForOtherWithException(contract, userC, 0);
    uncdForOtherWithException(contract, userC, 2);

    clearDelegatedExpireTime(contract, userA);

    uncdForOtherWithException(contract, userA, 2);
    uncdForOther(contract, userA, 0);
    uncdForOther(contract, userA, 1);
    uncdForOtherWithException(contract, userA, 0);
    uncdForOtherWithException(contract, userA, 1);
  }

  @Test
  public void testCdAndUncdToCreate2Contract() throws Exception {
    byte[] factoryAddr = deployContract("FactoryContract", FACTORY_CODE);
    byte[] contractAddr = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;
    long salt = 1;
    byte[] predictedAddr = getCreate2Addr(factoryAddr, salt);
    Assert.assertNull(manager.getAccountStore().get(predictedAddr));
    cdForOther(contractAddr, predictedAddr, cdedBalance, 0);
    Assert.assertNotNull(manager.getAccountStore().get(predictedAddr));
    cdForOther(contractAddr, predictedAddr, cdedBalance, 1);
    uncdForOtherWithException(contractAddr, predictedAddr, 0);
    uncdForOtherWithException(contractAddr, predictedAddr, 1);
    clearDelegatedExpireTime(contractAddr, predictedAddr);
    uncdForOther(contractAddr, predictedAddr, 0);
    uncdForOther(contractAddr, predictedAddr, 1);

    cdForOther(contractAddr, predictedAddr, cdedBalance, 0);
    cdForOther(contractAddr, predictedAddr, cdedBalance, 1);
    Assert.assertArrayEquals(predictedAddr, deployCreate2Contract(factoryAddr, salt));
    cdForOtherWithException(contractAddr, predictedAddr, cdedBalance, 0);
    cdForOtherWithException(contractAddr, predictedAddr, cdedBalance, 1);
    clearDelegatedExpireTime(contractAddr, predictedAddr);
    uncdForOther(contractAddr, predictedAddr, 0);
    uncdForOther(contractAddr, predictedAddr, 1);
    uncdForOtherWithException(contractAddr, predictedAddr, 0);
    uncdForOtherWithException(contractAddr, predictedAddr, 1);

    setBalance(predictedAddr, 100_000_000);
    cdForSelf(predictedAddr, cdedBalance, 0);
    cdForSelf(predictedAddr, cdedBalance, 1);
    cdForOther(predictedAddr, userA, cdedBalance, 0);
    cdForOther(predictedAddr, userA, cdedBalance, 1);
    clearExpireTime(predictedAddr);
    uncdForSelf(predictedAddr, 0);
    uncdForSelf(predictedAddr, 1);
    clearDelegatedExpireTime(predictedAddr, userA);
    uncdForOther(predictedAddr, userA, 0);
    uncdForOther(predictedAddr, userA, 1);
  }

  @Test
  public void testContractSuicideToBlackHole() throws Exception {
    byte[] contract = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    cdForOther(contract, userA, cdedBalance, 0);
    cdForOther(contract, userA, cdedBalance, 1);
    cdForOther(contract, userB, cdedBalance, 0);
    cdForOther(contract, userB, cdedBalance, 1);
    suicideWithException(contract, contract);
    clearDelegatedExpireTime(contract, userA);
    uncdForOther(contract, userA, 0);
    uncdForOther(contract, userA, 1);
    suicideWithException(contract, contract);
    clearDelegatedExpireTime(contract, userB);
    uncdForOther(contract, userB, 0);
    uncdForOther(contract, userB, 1);
    suicideToAccount(contract, contract);
  }

  @Test
  public void testContractSuicideToNonExistAccount() throws Exception {
    byte[] contract = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    cdForOther(contract, userA, cdedBalance, 0);
    cdForOther(contract, userA, cdedBalance, 1);
    suicideWithException(contract, userB);
    clearDelegatedExpireTime(contract, userA);
    uncdForOther(contract, userA, 0);
    uncdForOther(contract, userA, 1);
    suicideToAccount(contract, userB);
  }

  @Test
  public void testContractSuicideToExistNormalAccount() throws Exception {
    byte[] contract = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    cdForOther(contract, userA, cdedBalance, 0);
    cdForOther(contract, userA, cdedBalance, 1);
    suicideWithException(contract, userA);
    clearDelegatedExpireTime(contract, userA);
    uncdForOther(contract, userA, 0);
    uncdForOther(contract, userA, 1);
    suicideToAccount(contract, userA);
  }

  @Test
  public void testContractSuicideToExistContractAccount() throws Exception {
    byte[] contract = deployContract("TestCd", CONTRACT_CODE);
    byte[] otherContract = deployContract("OtherTestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    cdForOther(contract, userA, cdedBalance, 0);
    cdForOther(contract, userA, cdedBalance, 1);
    suicideWithException(contract, otherContract);
    clearDelegatedExpireTime(contract, userA);
    uncdForOther(contract, userA, 0);
    uncdForOther(contract, userA, 1);
    cdForSelf(otherContract, cdedBalance, 0);
    cdForSelf(otherContract, cdedBalance, 1);
    cdForOther(otherContract, userA, cdedBalance, 0);
    cdForOther(otherContract, userA, cdedBalance, 1);
    suicideToAccount(contract, otherContract);
    suicideWithException(otherContract, contract);
    clearDelegatedExpireTime(otherContract, userA);
    uncdForOther(otherContract, userA, 0);
    uncdForOther(otherContract, userA, 1);
    suicideToAccount(otherContract, contract);
  }

  @Test
  public void testCreate2SuicideToBlackHole() throws Exception {
    byte[] factory = deployContract("FactoryContract", FACTORY_CODE);
    byte[] contract = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    long salt = 1;
    byte[] predictedAddr = getCreate2Addr(factory, salt);
    cdForOther(contract, predictedAddr, cdedBalance, 0);
    cdForOther(contract, predictedAddr, cdedBalance, 1);
    Assert.assertArrayEquals(predictedAddr, deployCreate2Contract(factory, salt));
    setBalance(predictedAddr, 100_000_000);
    cdForSelf(predictedAddr, cdedBalance, 0);
    cdForSelf(predictedAddr, cdedBalance, 1);
    cdForOther(predictedAddr, userA, cdedBalance, 0);
    cdForOther(predictedAddr, userA, cdedBalance, 1);
    suicideWithException(predictedAddr, predictedAddr);
    clearDelegatedExpireTime(predictedAddr, userA);
    uncdForOther(predictedAddr, userA, 0);
    uncdForOther(predictedAddr, userA, 1);
    suicideToAccount(predictedAddr, predictedAddr);

    uncdForOtherWithException(contract, predictedAddr, 0);
    uncdForOtherWithException(contract, predictedAddr, 1);
    clearDelegatedExpireTime(contract, predictedAddr);
    uncdForOther(contract, predictedAddr, 0);
    uncdForOther(contract, predictedAddr, 1);
  }

  @Test
  public void testCreate2SuicideToAccount() throws Exception {
    byte[] factory = deployContract("FactoryContract", FACTORY_CODE);
    byte[] contract = deployContract("TestCd", CONTRACT_CODE);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    long salt = 1;
    byte[] predictedAddr = getCreate2Addr(factory, salt);
    cdForOther(contract, predictedAddr, cdedBalance, 0);
    cdForOther(contract, predictedAddr, cdedBalance, 1);
    Assert.assertArrayEquals(predictedAddr, deployCreate2Contract(factory, salt));
    setBalance(predictedAddr, 100_000_000);
    cdForSelf(predictedAddr, cdedBalance, 0);
    cdForSelf(predictedAddr, cdedBalance, 1);
    cdForOther(predictedAddr, userA, cdedBalance, 0);
    cdForOther(predictedAddr, userA, cdedBalance, 1);
    suicideWithException(predictedAddr, contract);
    clearDelegatedExpireTime(predictedAddr, userA);
    uncdForOther(predictedAddr, userA, 0);
    uncdForOther(predictedAddr, userA, 1);
    suicideToAccount(predictedAddr, contract);

    uncdForOtherWithException(contract, predictedAddr, 0);
    uncdForOtherWithException(contract, predictedAddr, 1);
    clearDelegatedExpireTime(contract, predictedAddr);
    uncdForOther(contract, predictedAddr, 0);
    uncdForOther(contract, predictedAddr, 1);
  }

  @Test
  public void testCdUcrToCaller() throws Exception {
    byte[] contract = deployContract(owner, "TestCd", CONTRACT_CODE, 50, 10_000);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    setBalance(userA, 100_000_000);
    setCdedForUcr(owner, cdedBalance);
    AccountCapsule caller = manager.getAccountStore().get(userA);
    AccountCapsule deployer = manager.getAccountStore().get(owner);
    SVMTestResult result = cdForOther(userA, contract, userA, cdedBalance, 1);
    checkReceipt(result, caller, deployer);
  }

  @Test
  public void testCdUcrToDeployer() throws Exception {
    byte[] contract = deployContract(owner, "TestCd", CONTRACT_CODE, 50, 10_000);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    setBalance(userA, 100_000_000);
    setCdedForUcr(owner, cdedBalance);
    AccountCapsule caller = manager.getAccountStore().get(userA);
    AccountCapsule deployer = manager.getAccountStore().get(owner);
    SVMTestResult result = cdForOther(userA, contract, owner, cdedBalance, 1);
    checkReceipt(result, caller, deployer);
  }

  @Test
  public void testUncdUcrToCaller() throws Exception {
    byte[] contract = deployContract(owner, "TestCd", CONTRACT_CODE, 50, 10_000);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    setBalance(userA, 100_000_000);
    //setCdedForUcr(owner, cdedBalance);
    cdForOther(contract, userA, cdedBalance, 1);
    cdForOther(contract, owner, cdedBalance, 1);
    clearDelegatedExpireTime(contract, userA);
    AccountCapsule caller = manager.getAccountStore().get(userA);
    AccountCapsule deployer = manager.getAccountStore().get(owner);
    SVMTestResult result = uncdForOther(userA, contract, userA, 1);
    checkReceipt(result, caller, deployer);
  }

  @Test
  public void testUncdUcrToDeployer() throws Exception {
    byte[] contract = deployContract(owner, "TestCd", CONTRACT_CODE, 50, 10_000);
    long cdedBalance = 1_000_000;
    cdForSelf(contract, cdedBalance, 0);
    cdForSelf(contract, cdedBalance, 1);
    setBalance(userA, 100_000_000);
    //setCdedForUcr(owner, cdedBalance);
    cdForOther(contract, userA, cdedBalance, 1);
    cdForOther(contract, owner, cdedBalance, 1);
    clearDelegatedExpireTime(contract, owner);
    AccountCapsule caller = manager.getAccountStore().get(userA);
    AccountCapsule deployer = manager.getAccountStore().get(owner);
    SVMTestResult result = uncdForOther(userA, contract, owner, 1);
    checkReceipt(result, caller, deployer);
  }

  private void clearExpireTime(byte[] owner) {
    AccountCapsule accountCapsule = manager.getAccountStore().get(owner);
    long now = manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
    accountCapsule.setCdedForBandwidth(accountCapsule.getCdedBalance(), now);
    accountCapsule.setCdedForUcr(accountCapsule.getUcrCdedBalance(), now);
    manager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
  }

  private void clearDelegatedExpireTime(byte[] owner,
                                        byte[] receiver) {
    byte[] key = DelegatedResourceCapsule.createDbKey(owner, receiver);
    DelegatedResourceCapsule delegatedResource = manager.getDelegatedResourceStore().get(key);
    long now = manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
    delegatedResource.setExpireTimeForBandwidth(now);
    delegatedResource.setExpireTimeForUcr(now);
    manager.getDelegatedResourceStore().put(key, delegatedResource);
  }

  private SVMTestResult cdForSelf(byte[] contractAddr,
                                      long cdedBalance,
                                      long res) throws Exception {
    return cdForSelf(owner, contractAddr, cdedBalance, res);
  }

  private SVMTestResult cdForSelf(byte[] callerAddr,
                                      byte[] contractAddr,
                                      long cdedBalance,
                                      long res) throws Exception {
    DynamicPropertiesStore dynamicStore = manager.getDynamicPropertiesStore();
    long oldTotalNetWeight = dynamicStore.getTotalNetWeight();
    long oldTotalUcrWeight = dynamicStore.getTotalUcrWeight();

    AccountStore accountStore = manager.getAccountStore();
    AccountCapsule oldOwner = accountStore.get(contractAddr);

    SVMTestResult result = triggerCd(callerAddr, contractAddr, contractAddr, cdedBalance, res,
        SUCCESS,
        returnValue -> Assert.assertEquals(dynamicStore.getMinCdedTime() * CDED_PERIOD,
            new DataWord(returnValue).longValue() * 1000));

    AccountCapsule newOwner = accountStore.get(contractAddr);
    Assert.assertEquals(oldOwner.getBalance() - cdedBalance, newOwner.getBalance());
    newOwner.setBalance(oldOwner.getBalance());
    if (res == 0) {
      Assert.assertEquals(1, newOwner.getCdedCount());
      Assert.assertEquals(oldOwner.getCdedBalance() + cdedBalance, newOwner.getCdedBalance());
      Assert.assertEquals(oldTotalNetWeight + cdedBalance / STB_PRECISION,
          dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight, dynamicStore.getTotalUcrWeight());
      oldOwner.setCdedForBandwidth(0, 0);
      newOwner.setCdedForBandwidth(0, 0);
    } else {
      Assert.assertEquals(oldOwner.getUcrCdedBalance() + cdedBalance,
          newOwner.getUcrCdedBalance());
      Assert.assertEquals(oldTotalNetWeight, dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight + cdedBalance / STB_PRECISION,
          dynamicStore.getTotalUcrWeight());
      oldOwner.setCdedForUcr(0, 0);
      newOwner.setCdedForUcr(0, 0);
    }
    Assert.assertArrayEquals(oldOwner.getData(), newOwner.getData());

    return result;
  }

  private SVMTestResult cdForSelfWithException(byte[] contractAddr,
                                                   long cdedBalance,
                                                   long res) throws Exception {
    return triggerCd(owner, contractAddr, contractAddr, cdedBalance, res, REVERT, null);
  }

  private SVMTestResult uncdForSelf(byte[] contractAddr,
                                        long res) throws Exception {
    return uncdForSelf(owner, contractAddr, res);
  }

  private SVMTestResult uncdForSelf(byte[] callerAddr,
                                        byte[] contractAddr,
                                        long res) throws Exception {
    DynamicPropertiesStore dynamicStore = manager.getDynamicPropertiesStore();
    long oldTotalNetWeight = dynamicStore.getTotalNetWeight();
    long oldTotalUcrWeight = dynamicStore.getTotalUcrWeight();

    AccountStore accountStore = manager.getAccountStore();
    AccountCapsule oldOwner = accountStore.get(contractAddr);
    long cdedBalance = res == 0 ? oldOwner.getCdedBalance() : oldOwner.getUcrCdedBalance();
    Assert.assertTrue(cdedBalance > 0);

    SVMTestResult result = triggerUncd(callerAddr, contractAddr, contractAddr, res, SUCCESS,
        returnValue ->
            Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000001",
                Hex.toHexString(returnValue)));

    AccountCapsule newOwner = accountStore.get(contractAddr);
    Assert.assertEquals(oldOwner.getBalance() + cdedBalance, newOwner.getBalance());
    oldOwner.setBalance(newOwner.getBalance());
    if (res == 0) {
      Assert.assertEquals(0, newOwner.getCdedCount());
      Assert.assertEquals(0, newOwner.getCdedBalance());
      Assert.assertEquals(oldTotalNetWeight - cdedBalance / STB_PRECISION,
          dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight, dynamicStore.getTotalUcrWeight());
      oldOwner.setCdedForBandwidth(0, 0);
      newOwner.setCdedForBandwidth(0, 0);
    } else {
      Assert.assertEquals(0, newOwner.getUcrCdedBalance());
      Assert.assertEquals(oldTotalNetWeight, dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight - cdedBalance / STB_PRECISION,
          dynamicStore.getTotalUcrWeight());
      oldOwner.setCdedForUcr(0, 0);
      newOwner.setCdedForUcr(0, 0);
    }
    Assert.assertArrayEquals(oldOwner.getData(), newOwner.getData());

    return result;
  }

  private SVMTestResult uncdForSelfWithException(byte[] contractAddr,
                                                     long res) throws Exception {
    return triggerUncd(owner, contractAddr, contractAddr, res, REVERT, null);
  }

  private SVMTestResult cdForOther(
      byte[] contractAddr,
      byte[] receiverAddr,
      long cdedBalance,
      long res) throws Exception {
    return cdForOther(owner, contractAddr, receiverAddr, cdedBalance, res);
  }

  private SVMTestResult cdForOther(byte[] callerAddr,
                                       byte[] contractAddr,
                                       byte[] receiverAddr,
                                       long cdedBalance,
                                       long res) throws Exception {
    DynamicPropertiesStore dynamicStore = manager.getDynamicPropertiesStore();
    long oldTotalNetWeight = dynamicStore.getTotalNetWeight();
    long oldTotalUcrWeight = dynamicStore.getTotalUcrWeight();

    AccountStore accountStore = manager.getAccountStore();
    AccountCapsule oldOwner = accountStore.get(contractAddr);
    Assert.assertNotNull(receiverAddr);
    AccountCapsule oldReceiver = accountStore.get(receiverAddr);
    long acquiredBalance = 0;
    if (oldReceiver != null) {
      acquiredBalance = res == 0 ? oldReceiver.getAcquiredDelegatedCdedBalanceForBandwidth() :
          oldReceiver.getAcquiredDelegatedCdedBalanceForUcr();
    }

    DelegatedResourceStore delegatedResourceStore = manager.getDelegatedResourceStore();
    DelegatedResourceCapsule oldDelegatedResource = delegatedResourceStore.get(
        DelegatedResourceCapsule.createDbKey(contractAddr, receiverAddr));
    if (oldDelegatedResource == null) {
      oldDelegatedResource = new DelegatedResourceCapsule(
          ByteString.copyFrom(contractAddr),
          ByteString.copyFrom(receiverAddr));
    }

    SVMTestResult result = triggerCd(callerAddr, contractAddr, receiverAddr, cdedBalance, res,
        SUCCESS,
        returnValue -> Assert.assertEquals(dynamicStore.getMinCdedTime() * CDED_PERIOD,
            new DataWord(returnValue).longValue() * 1000));

    AccountCapsule newOwner = accountStore.get(contractAddr);
    Assert.assertEquals(oldOwner.getBalance() - cdedBalance, newOwner.getBalance());
    newOwner.setBalance(oldOwner.getBalance());
    if (res == 0) {
      Assert.assertEquals(oldOwner.getDelegatedCdedBalanceForBandwidth() + cdedBalance,
          newOwner.getDelegatedCdedBalanceForBandwidth());
      oldOwner.setDelegatedCdedBalanceForBandwidth(0);
      newOwner.setDelegatedCdedBalanceForBandwidth(0);
    } else {
      Assert.assertEquals(oldOwner.getDelegatedCdedBalanceForUcr() + cdedBalance,
          newOwner.getDelegatedCdedBalanceForUcr());
      oldOwner.setDelegatedCdedBalanceForUcr(0);
      newOwner.setDelegatedCdedBalanceForUcr(0);
    }
    Assert.assertArrayEquals(oldOwner.getData(), newOwner.getData());

    AccountCapsule newReceiver = accountStore.get(receiverAddr);
    Assert.assertNotNull(newReceiver);
    Assert.assertEquals(acquiredBalance + cdedBalance,
        res == 0 ? newReceiver.getAcquiredDelegatedCdedBalanceForBandwidth() :
            newReceiver.getAcquiredDelegatedCdedBalanceForUcr());
    if (oldReceiver != null) {
      newReceiver.setBalance(oldReceiver.getBalance());
      oldReceiver.setUcrUsage(0);
      newReceiver.setUcrUsage(0);
      if (res == 0) {
        oldReceiver.setAcquiredDelegatedCdedBalanceForBandwidth(0);
        newReceiver.setAcquiredDelegatedCdedBalanceForBandwidth(0);
      } else {
        oldReceiver.setAcquiredDelegatedCdedBalanceForUcr(0);
        newReceiver.setAcquiredDelegatedCdedBalanceForUcr(0);
      }
      Assert.assertArrayEquals(oldReceiver.getData(), newReceiver.getData());
    }

    DelegatedResourceCapsule newDelegatedResource = delegatedResourceStore.get(
        DelegatedResourceCapsule.createDbKey(contractAddr, receiverAddr));
    Assert.assertNotNull(newDelegatedResource);
    if (res == 0) {
      Assert.assertEquals(cdedBalance + oldDelegatedResource.getCdedBalanceForBandwidth(),
          newDelegatedResource.getCdedBalanceForBandwidth());
      Assert.assertEquals(oldDelegatedResource.getCdedBalanceForUcr(),
          newDelegatedResource.getCdedBalanceForUcr());
    } else {
      Assert.assertEquals(oldDelegatedResource.getCdedBalanceForBandwidth(),
          newDelegatedResource.getCdedBalanceForBandwidth());
      Assert.assertEquals(cdedBalance + oldDelegatedResource.getCdedBalanceForUcr(),
          newDelegatedResource.getCdedBalanceForUcr());
    }

    if (res == 0) {
      Assert.assertEquals(oldTotalNetWeight + cdedBalance / STB_PRECISION,
          dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight, dynamicStore.getTotalUcrWeight());
    } else {
      Assert.assertEquals(oldTotalNetWeight, dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight + cdedBalance / STB_PRECISION,
          dynamicStore.getTotalUcrWeight());
    }

    return result;
  }

  private SVMTestResult cdForOtherWithException(
      byte[] contractAddr,
      byte[] receiverAddr,
      long cdedBalance,
      long res) throws Exception {
    return triggerCd(owner, contractAddr, receiverAddr, cdedBalance, res, REVERT, null);
  }

  private SVMTestResult uncdForOther(byte[] contractAddr,
                                         byte[] receiverAddr,
                                         long res) throws Exception {
    return uncdForOther(owner, contractAddr, receiverAddr, res);
  }

  private SVMTestResult uncdForOther(byte[] callerAddr,
                                         byte[] contractAddr,
                                         byte[] receiverAddr,
                                         long res) throws Exception {
    DynamicPropertiesStore dynamicStore = manager.getDynamicPropertiesStore();
    long oldTotalNetWeight = dynamicStore.getTotalNetWeight();
    long oldTotalUcrWeight = dynamicStore.getTotalUcrWeight();

    AccountStore accountStore = manager.getAccountStore();
    AccountCapsule oldOwner = accountStore.get(contractAddr);
    long delegatedBalance = res == 0 ? oldOwner.getDelegatedCdedBalanceForBandwidth() :
        oldOwner.getDelegatedCdedBalanceForUcr();

    AccountCapsule oldReceiver = accountStore.get(receiverAddr);
    long acquiredBalance = 0;
    if (oldReceiver != null) {
      acquiredBalance = res == 0 ? oldReceiver.getAcquiredDelegatedCdedBalanceForBandwidth() :
          oldReceiver.getAcquiredDelegatedCdedBalanceForUcr();
    }

    DelegatedResourceStore delegatedResourceStore = manager.getDelegatedResourceStore();
    DelegatedResourceCapsule oldDelegatedResource = delegatedResourceStore.get(
        DelegatedResourceCapsule.createDbKey(contractAddr, receiverAddr));
    Assert.assertNotNull(oldDelegatedResource);
    long delegatedCdedBalance = res == 0 ? oldDelegatedResource.getCdedBalanceForBandwidth() :
        oldDelegatedResource.getCdedBalanceForUcr();
    Assert.assertTrue(delegatedCdedBalance > 0);
    Assert.assertTrue(delegatedCdedBalance <= delegatedBalance);

    SVMTestResult result = triggerUncd(callerAddr, contractAddr, receiverAddr, res, SUCCESS,
        returnValue ->
            Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000001",
                Hex.toHexString(returnValue)));

    // check owner account
    AccountCapsule newOwner = accountStore.get(contractAddr);
    Assert.assertEquals(oldOwner.getBalance() + delegatedCdedBalance, newOwner.getBalance());
    newOwner.setBalance(oldOwner.getBalance());
    if (res == 0) {
      Assert.assertEquals(oldOwner.getDelegatedCdedBalanceForBandwidth() - delegatedCdedBalance,
          newOwner.getDelegatedCdedBalanceForBandwidth());
      newOwner.setDelegatedCdedBalanceForBandwidth(
          oldOwner.getDelegatedCdedBalanceForBandwidth());
    } else {
      Assert.assertEquals(oldOwner.getDelegatedCdedBalanceForUcr() - delegatedCdedBalance,
          newOwner.getDelegatedCdedBalanceForUcr());
      newOwner.setDelegatedCdedBalanceForUcr(oldOwner.getDelegatedCdedBalanceForUcr());
    }
    Assert.assertArrayEquals(oldOwner.getData(), newOwner.getData());

    // check receiver account
    AccountCapsule newReceiver = accountStore.get(receiverAddr);
    if (oldReceiver != null) {
      Assert.assertNotNull(newReceiver);
      long newAcquiredBalance = res == 0
          ? newReceiver.getAcquiredDelegatedCdedBalanceForBandwidth()
          : newReceiver.getAcquiredDelegatedCdedBalanceForUcr();
      Assert.assertTrue(newAcquiredBalance == 0
          || acquiredBalance - newAcquiredBalance == delegatedCdedBalance);
      newReceiver.setBalance(oldReceiver.getBalance());
      newReceiver.setUcrUsage(0);
      oldReceiver.setUcrUsage(0);
      if (res == 0) {
        oldReceiver.setAcquiredDelegatedCdedBalanceForBandwidth(0);
        newReceiver.setAcquiredDelegatedCdedBalanceForBandwidth(0);
      } else {
        oldReceiver.setAcquiredDelegatedCdedBalanceForUcr(0);
        newReceiver.setAcquiredDelegatedCdedBalanceForUcr(0);
      }
      Assert.assertArrayEquals(oldReceiver.getData(), newReceiver.getData());
    } else {
      Assert.assertNull(newReceiver);
    }

    // check delegated resource store
    DelegatedResourceCapsule newDelegatedResource = delegatedResourceStore.get(
        DelegatedResourceCapsule.createDbKey(contractAddr, receiverAddr));
    Assert.assertNotNull(newDelegatedResource);
    if (res == 0) {
      Assert.assertEquals(0, newDelegatedResource.getCdedBalanceForBandwidth());
      Assert.assertEquals(oldDelegatedResource.getCdedBalanceForUcr(),
          newDelegatedResource.getCdedBalanceForUcr());
    } else {
      Assert.assertEquals(oldDelegatedResource.getCdedBalanceForBandwidth(),
          newDelegatedResource.getCdedBalanceForBandwidth());
      Assert.assertEquals(0, newDelegatedResource.getCdedBalanceForUcr());
    }

    // check total weight
    if (res == 0) {
      Assert.assertEquals(oldTotalNetWeight - delegatedCdedBalance / STB_PRECISION,
          dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight, dynamicStore.getTotalUcrWeight());
    } else {
      Assert.assertEquals(oldTotalNetWeight, dynamicStore.getTotalNetWeight());
      Assert.assertEquals(oldTotalUcrWeight - delegatedCdedBalance / STB_PRECISION,
          dynamicStore.getTotalUcrWeight());
    }

    return result;
  }

  private SVMTestResult uncdForOtherWithException(byte[] contractAddr,
                                                      byte[] receiverAddr,
                                                      long res) throws Exception {
    return triggerUncd(owner, contractAddr, receiverAddr, res, REVERT, null);
  }

  private SVMTestResult suicideWithException(byte[] contractAddr,
                                             byte[] inheritorAddr) throws Exception {
    return triggerSuicide(owner, contractAddr, inheritorAddr, REVERT, null);
  }

  private SVMTestResult suicideToAccount(byte[] contractAddr,
                                         byte[] inheritorAddr) throws Exception {
    return suicideToAccount(owner, contractAddr, inheritorAddr);
  }

  private SVMTestResult suicideToAccount(byte[] callerAddr,
                                         byte[] contractAddr,
                                         byte[] inheritorAddr) throws Exception {
    if (FastByteComparisons.isEqual(contractAddr, inheritorAddr)) {
      inheritorAddr = manager.getAccountStore().getBlackholeAddress();
    }
    DynamicPropertiesStore dynamicStore = manager.getDynamicPropertiesStore();
    long oldTotalNetWeight = dynamicStore.getTotalNetWeight();
    long oldTotalUcrWeight = dynamicStore.getTotalUcrWeight();

    AccountStore accountStore = manager.getAccountStore();
    AccountCapsule contract = accountStore.get(contractAddr);
    AccountCapsule oldInheritor = accountStore.get(inheritorAddr);
    long oldBalanceOfInheritor = 0;
    if (oldInheritor != null) {
      oldBalanceOfInheritor = oldInheritor.getBalance();
    }

    SVMTestResult result = triggerSuicide(callerAddr, contractAddr, inheritorAddr, SUCCESS, null);

    Assert.assertNull(accountStore.get(contractAddr));
    AccountCapsule newInheritor = accountStore.get(inheritorAddr);
    Assert.assertNotNull(newInheritor);
    if (FastByteComparisons.isEqual(inheritorAddr,
        manager.getAccountStore().getBlackholeAddress())) {
      Assert.assertEquals(contract.getBalance() + contract.getStabilaPower(),
          newInheritor.getBalance() - oldBalanceOfInheritor - result.getReceipt().getUcrFee());
    } else {
      Assert.assertEquals(contract.getBalance() + contract.getStabilaPower(),
          newInheritor.getBalance() - oldBalanceOfInheritor);
    }

    Assert.assertEquals(0, contract.getDelegatedCdedBalanceForBandwidth());
    Assert.assertEquals(0, contract.getDelegatedCdedBalanceForUcr());

    long newTotalNetWeight = dynamicStore.getTotalNetWeight();
    long newTotalUcrWeight = dynamicStore.getTotalUcrWeight();
    Assert.assertEquals(contract.getCdedBalance(),
        (oldTotalNetWeight - newTotalNetWeight) * STB_PRECISION);
    Assert.assertEquals(contract.getUcrCdedBalance(),
        (oldTotalUcrWeight - newTotalUcrWeight) * STB_PRECISION);

    return result;
  }

  private void checkReceipt(SVMTestResult result,
                            AccountCapsule caller,
                            AccountCapsule deployer) {
    AccountStore accountStore = manager.getAccountStore();
    long callerUcrUsage = result.getReceipt().getUcrUsage();
    long deployerUcrUsage = result.getReceipt().getOriginUcrUsage();
    long burnedStb = result.getReceipt().getUcrFee();
    AccountCapsule newCaller = accountStore.get(caller.createDbKey());
    Assert.assertEquals(callerUcrUsage,
        newCaller.getUcrUsage() - caller.getUcrUsage());
    Assert.assertEquals(deployerUcrUsage,
        accountStore.get(deployer.createDbKey()).getUcrUsage() - deployer.getUcrUsage());
    Assert.assertEquals(burnedStb,
        caller.getBalance() - accountStore.get(caller.createDbKey()).getBalance());
  }

  @After
  public void destroy() {
    ConfigLoader.disable = false;
    VMConfig.initVmHardFork(false);
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.error("Release resources failure.");
    }
  }
}
