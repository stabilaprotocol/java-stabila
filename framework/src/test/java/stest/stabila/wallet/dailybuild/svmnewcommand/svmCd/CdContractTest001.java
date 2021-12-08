package stest.stabila.wallet.dailybuild.svmnewcommand.svmCd;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI.AccountResourceMessage;
import org.stabila.api.GrpcAPI.TransactionExtention;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Transaction.Result.contractResult;
import org.stabila.protos.Protocol.TransactionInfo;
import org.stabila.protos.Protocol.TransactionInfo.code;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class CdContractTest001 {

  private String testFoundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private byte[] testFoundationAddress = PublicMethed.getFinalAddress(testFoundationKey);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] testAddress001 = ecKey1.getAddress();
  String testKey001 = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private byte[] contractAddress;


  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] testAddress002 = ecKey2.getAddress();
  String testKey002 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  ECKey ecKey3 = new ECKey(Utils.getRandom());
  byte[] testAddress003 = ecKey3.getAddress();
  String testKey003 = ByteArray.toHexString(ecKey3.getPrivKeyBytes());

  private long cdUcrUseage;
  private byte[] create2Address;
  private final long cdCount = 1000_123456L;


  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    PublicMethed.printAddress(testKey001);
    PublicMethed.printAddress(testKey002);

    PublicMethed.printAddress(testFoundationKey);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    Assert.assertTrue(PublicMethed.sendcoin(testAddress001,2000_000000L,
        testFoundationAddress,testFoundationKey,blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(testAddress002,10_000000L,
        testFoundationAddress,testFoundationKey,blockingStubFull));
    Assert.assertTrue(PublicMethed.sendcoin(testAddress003,12000_000000L,
        testFoundationAddress,testFoundationKey,blockingStubFull));

    String filePath = "src/test/resources/soliditycode/cdContract001.sol";
    String contractName = "TestCd";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 10000_000000L,
            100, null, testFoundationKey,
            testFoundationAddress, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
  }


  @Test(description = "contract cd to account")
  void CdContractTest001() {

    AccountResourceMessage account002_before = PublicMethed
        .getAccountResource(testAddress002,blockingStubFull);
    Account contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "cd(address,uint256,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testAddress002) + "\"," + cdCount + "," + "1";
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage account002_after = PublicMethed
        .getAccountResource(testAddress002,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_before.getUcrLimit : " + account002_before.getUcrLimit());
    logger.info("account002_after.getUcrLimit : " + account002_after.getUcrLimit());
    Assert.assertTrue(account002_before.getUcrLimit() < account002_after.getUcrLimit());
    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getDelegatedCdedBalanceForUcr() + cdCount,
        contractAccount_after.getAccountResource().getDelegatedCdedBalanceForUcr());
    Assert.assertEquals(contractAccount_before.getBalance() - cdCount,
        contractAccount_after.getBalance());

    TransactionInfo info = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get();
    cdUcrUseage = info.getReceipt().getUcrUsageTotal();


  }

  @Test(description = "contract cd to self")
  void CdContractTest002() {
    AccountResourceMessage contractResource_before = PublicMethed
        .getAccountResource(contractAddress,blockingStubFull);
    Account contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "cd(address,uint256,uint256)";
    String argsStr = "\"" + Base58.encode58Check(contractAddress) + "\"," + cdCount + "," + "1";
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage contractResource_after = PublicMethed
        .getAccountResource(contractAddress,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_before.getUcrLimit : " + contractResource_before.getUcrLimit());
    logger.info("account002_after.getUcrLimit : " + contractResource_after.getUcrLimit());
    Assert.assertTrue(
        contractResource_before.getUcrLimit() < contractResource_after.getUcrLimit());
    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getCdedBalanceForUcr().getCdedBalance() + cdCount,
        contractAccount_after.getAccountResource().getCdedBalanceForUcr().getCdedBalance());

  }

  @Test(description = "contract cd to other contract")
  void CdContractTest003() {
    String filePath = "src/test/resources/soliditycode/cdContract001.sol";
    String contractName = "TestCd";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    byte[] newContract = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 100_000000L,
            100, null, testFoundationKey,
            testFoundationAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);


    Account contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "cd(address,uint256,uint256)";
    String argsStr = "\"" + Base58.encode58Check(newContract) + "\"," + cdCount + "," + "1";
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    TransactionInfo info = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get();
    Assert.assertEquals(TransactionInfo.code.FAILED,info.getResult());

    AccountResourceMessage contractResource_after = PublicMethed
        .getAccountResource(newContract,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_after.getUcrLimit : " + contractResource_after.getUcrLimit());
    Assert.assertEquals(contractResource_after.getUcrLimit(),0);
    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getDelegatedCdedBalanceForUcr(),
        contractAccount_after.getAccountResource().getDelegatedCdedBalanceForUcr());
    Assert.assertEquals(contractAccount_before.getBalance(),contractAccount_after.getBalance());

  }

  @Test(description = "contract cd to unactive account",
      dependsOnMethods = "CdContractTest001")
  void CdContractTest004() {

    ECKey ecKey = new ECKey(Utils.getRandom());
    byte[] testAddress = ecKey.getAddress();
    String testKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());

    Account contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "cd(address,uint256,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testAddress) + "\"," + cdCount + "," + "1";
    logger.info("argsStr: " + argsStr);

    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage account002_after = PublicMethed
        .getAccountResource(testAddress,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_after.getUcrLimit : " + account002_after.getUcrLimit());
    Assert.assertTrue(account002_after.getUcrLimit() > 0);
    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getDelegatedCdedBalanceForUcr() + cdCount,
        contractAccount_after.getAccountResource().getDelegatedCdedBalanceForUcr());
    Assert.assertEquals(contractAccount_before.getBalance() - cdCount,
        contractAccount_after.getBalance());

    // check active account status
    Account testAccount = PublicMethed.queryAccount(testAddress,blockingStubFull);
    Assert.assertTrue(testAccount.getCreateTime() > 0);
    Assert.assertNotNull(testAccount.getOwnerPermission());
    Assert.assertNotNull(testAccount.getActivePermissionList());


    TransactionInfo info = PublicMethed.getTransactionInfoById(txid,blockingStubFull).get();
    Assert.assertEquals(cdUcrUseage + 25000L, info.getReceipt().getUcrUsageTotal());


  }

  @Test(description = "contract cd to pre create2 address, and UnCd",
      dependsOnMethods = "CdContractTest001")
  void CdContractTest005() {
    String create2ArgsStr = "1";
    String create2MethedStr = "deploy(uint256)";
    TransactionExtention exten = PublicMethed.triggerConstantContractForExtention(
        contractAddress, create2MethedStr, create2ArgsStr, false, 0, maxFeeLimit,
        "#", 0, testAddress001, testKey001, blockingStubFull);

    String addressHex =
        "41" + ByteArray.toHexString(exten.getConstantResult(0).toByteArray())
            .substring(24);
    logger.info("address_hex: " + addressHex);
    create2Address = ByteArray.fromHexString(addressHex);
    logger.info("create2Address: " + Base58.encode58Check(create2Address));


    Account contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "cd(address,uint256,uint256)";
    String argsStr = "\"" + Base58.encode58Check(create2Address) + "\"," + cdCount + "," + "1";
    logger.info("argsStr: " + argsStr);
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage account002_after = PublicMethed
        .getAccountResource(create2Address,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_after.getUcrLimit : " + account002_after.getUcrLimit());
    Assert.assertTrue(account002_after.getUcrLimit() > 0);
    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getDelegatedCdedBalanceForUcr() + cdCount,
        contractAccount_after.getAccountResource().getDelegatedCdedBalanceForUcr());
    Assert.assertEquals(contractAccount_before.getBalance() - cdCount,
        contractAccount_after.getBalance());

    TransactionInfo info = PublicMethed.getTransactionInfoById(txid,blockingStubFull).get();
    Assert.assertEquals(cdUcrUseage + 25000L, info.getReceipt().getUcrUsageTotal());

    txid = PublicMethed.triggerContract(contractAddress,create2MethedStr,
        create2ArgsStr,false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);


    contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    methedStr = "getExpireTime(address,uint256)";
    argsStr = "\"" + Base58.encode58Check(create2Address) + "\"" + ",1";
    TransactionExtention extention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,methedStr,argsStr,
            false,0,maxFeeLimit,"#",0, testAddress001,testKey001,blockingStubFull);
    Long ExpireTime = ByteArray.toLong(extention.getConstantResult(0).toByteArray());
    logger.info("ExpireTime: " + ExpireTime);
    Assert.assertTrue(ExpireTime > 0);

    methedStr = "uncd(address,uint256)";
    txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getDelegatedCdedBalanceForUcr() - cdCount,
        contractAccount_after.getAccountResource().getDelegatedCdedBalanceForUcr());
    Assert.assertEquals(contractAccount_before.getBalance() + cdCount,
        contractAccount_after.getBalance());

  }

  @Test(description = "Uncd when cd to account",
      dependsOnMethods = "CdContractTest001")
  void UnCdContractTest001() {

    AccountResourceMessage account002_before = PublicMethed
        .getAccountResource(testAddress002,blockingStubFull);
    Account contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "getExpireTime(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testAddress002) + "\"" + ",1";
    TransactionExtention extention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,"#",0, testAddress001,testKey001,blockingStubFull);
    Long ExpireTime = ByteArray.toLong(extention.getConstantResult(0).toByteArray());
    logger.info("ExpireTime: " + ExpireTime);
    Assert.assertTrue(ExpireTime > 0);

    methedStr = "uncd(address,uint256)";
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage account002_after = PublicMethed
        .getAccountResource(testAddress002,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_before.getUcrLimit : " + account002_before.getUcrLimit());
    logger.info("account002_after.getUcrLimit : " + account002_after.getUcrLimit());

    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getDelegatedCdedBalanceForUcr() - cdCount,
        contractAccount_after.getAccountResource().getDelegatedCdedBalanceForUcr());

    Assert.assertTrue(account002_before.getUcrLimit() > account002_after.getUcrLimit());

  }

  @Test(description = "Uncd when cd to contract self",
      dependsOnMethods = "CdContractTest002")
  void UnCdContractTest002() {

    Account contractAccount_before = PublicMethed.queryAccount(contractAddress,blockingStubFull);

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "getExpireTime(address,uint256)";
    String argsStr = "\"" + Base58.encode58Check(contractAddress) + "\"" + ",1";
    TransactionExtention extention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,methedStr,argsStr,
            false,0,maxFeeLimit,"#",0, testAddress001,testKey001,blockingStubFull);
    Long ExpireTime = ByteArray.toLong(extention.getConstantResult(0).toByteArray());
    logger.info("ExpireTime: " + ExpireTime);
    Assert.assertTrue(ExpireTime > 0);

    methedStr = "uncd(address,uint256)";
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage account002_after = PublicMethed
        .getAccountResource(testAddress002,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_after.getUcrLimit : " + account002_after.getUcrLimit());

    Assert.assertEquals(contractAccount_before.getAccountResource()
            .getCdedBalanceForUcr().getCdedBalance() - cdCount,
        contractAccount_after.getAccountResource().getCdedBalanceForUcr().getCdedBalance());


  }

  @Test(description = "ucr caulate after transaction end")
  public void cdUcrCaulate() {

    String methedStr = "cd(address,uint256,uint256)";
    String argsStr = "\"" + Base58.encode58Check(testAddress001) + "\"," + cdCount + "," + "1";
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    TransactionInfo info = PublicMethed.getTransactionInfoById(txid,blockingStubFull).get();
    AccountResourceMessage testAccount001 = PublicMethed
        .getAccountResource(testAddress001,blockingStubFull);


    Assert.assertTrue(testAccount001.getUcrLimit() > 0);
    Assert.assertTrue(info.getReceipt().getUcrFee() > 0);
    Assert.assertTrue(testAccount001.getUcrLimit() > info.getReceipt().getUcrUsageTotal());

    methedStr = "uncd(address,uint256)";
    argsStr = "\"" + Base58.encode58Check(testAddress001) + "\",1";
    txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    info = PublicMethed.getTransactionInfoById(txid,blockingStubFull).get();
    testAccount001 = PublicMethed.getAccountResource(testAddress001,blockingStubFull);

    Assert.assertEquals(code.SUCESS,info.getResult());
    Assert.assertEquals(contractResult.SUCCESS,info.getReceipt().getResult());


    Assert.assertEquals(0, info.getReceipt().getUcrFee());
    Assert.assertEquals(0, testAccount001.getUcrLimit());
    Assert.assertTrue(testAccount001.getUcrUsed() > 0);
  }

  @Test(description = "get Zero Address ExpirTime,used to be that cd to contract self",
      dependsOnMethods = "CdContractTest002")
  public void getZeroExpireTimeTest() {
    String ExpireTimeMethedStr = "getExpireTime(address,uint256)";
    String ExpireTimeArgsStr = "\"T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb\"" + ",0";
    TransactionExtention extention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,ExpireTimeMethedStr,ExpireTimeArgsStr,
            false,0,maxFeeLimit,"#",0, testAddress001,testKey001,blockingStubFull);
    Long ExpireTime1 = ByteArray.toLong(extention.getConstantResult(0).toByteArray());
    logger.info("ExpireTime1: " + ExpireTime1);
    Assert.assertEquals(0,ExpireTime1.longValue());

    ExpireTimeArgsStr = "\"T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb\"" + ",1";
    extention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,ExpireTimeMethedStr,ExpireTimeArgsStr,
            false,0,maxFeeLimit,"#",0, testAddress001,testKey001,blockingStubFull);
    Long ExpireTime2 = ByteArray.toLong(extention.getConstantResult(0).toByteArray());
    logger.info("ExpireTime2: " + ExpireTime2);
    Assert.assertEquals(0,ExpireTime2.longValue());

    // cd(address payable receiver, uint amount, uint res)
    String methedStr = "cd(address,uint256,uint256)";
    String argsStr = "\"" + "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb" + "\"," + cdCount + "," + "1";
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argsStr,
        false,0,maxFeeLimit,testAddress001,testKey001,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    TransactionInfo info = PublicMethed.getTransactionInfoById(txid, blockingStubFull).get();
    Assert.assertEquals(code.SUCESS,info.getResult());
    Assert.assertEquals(contractResult.SUCCESS,info.getReceipt().getResult());

    extention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,ExpireTimeMethedStr,ExpireTimeArgsStr,
            false,0,maxFeeLimit,"#",0, testAddress001,testKey001,blockingStubFull);
    Long ExpireTime = ByteArray.toLong(extention.getConstantResult(0).toByteArray());
    logger.info("ExpireTime: " + ExpireTime);
    Assert.assertEquals((ExpireTime + 3) * 1000, info.getBlockTimeStamp());


  }

  @Test(description = "cd in constructor")
  public void CdContractTest006() {

    AccountResourceMessage account003_before = PublicMethed
        .getAccountResource(testAddress003,blockingStubFull);

    String filePath = "src/test/resources/soliditycode/cdContract001.sol";
    String contractName = "D";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    long callValue = 10000_000000L;
    byte[] contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, callValue,
            100, null, testKey003,
            testAddress003, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    AccountResourceMessage account003_after = PublicMethed
        .getAccountResource(testAddress003,blockingStubFull);
    Account contractAccount_after = PublicMethed.queryAccount(contractAddress, blockingStubFull);

    logger.info("account002_before.getUcrLimit : " + account003_before.getUcrLimit());
    logger.info("account002_after.getUcrLimit : " + account003_after.getUcrLimit());
    Assert.assertTrue(account003_before.getUcrLimit() < account003_after.getUcrLimit());
    Assert.assertEquals(callValue,
        contractAccount_after.getAccountResource().getDelegatedCdedBalanceForUcr());
    Assert.assertEquals(0, contractAccount_after.getBalance());
  }

}
