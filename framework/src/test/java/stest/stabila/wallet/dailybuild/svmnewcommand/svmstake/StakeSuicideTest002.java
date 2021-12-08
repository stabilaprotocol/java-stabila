package stest.stabila.wallet.dailybuild.svmnewcommand.svmstake;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Account.Cded;
import org.stabila.protos.Protocol.TransactionInfo;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;

@Slf4j
public class StakeSuicideTest002 {
  private String testFoundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private byte[] testFoundationAddress = PublicMethed.getFinalAddress(testFoundationKey);
  private String testExecutiveKey = Configuration.getByPath("testng.conf")
      .getString("executive.key1");
  private String testExecutiveAddress = PublicMethed.getAddressString(testExecutiveKey);

  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  private byte[] contractAddress;

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = false)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    String filePath = "src/test/resources/soliditycode/stackSuicide001.sol";
    String contractName = "B";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 10000000L,
            100, null, testFoundationKey,
            testFoundationAddress, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
  }

  @Test(enabled = false, description = "create2 -> stake -> suicide -> create2 the same Address")
  public void stackSuicideAndCreate2Test001() {

    String filePath = "src/test/resources/soliditycode/stackSuicide001.sol";
    String contractName = "testStakeSuicide";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();

    String methedStr = "deploy(bytes,uint256)";
    String argStr = "\"" + code + "\"," + 1;
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argStr,false,
        0,maxFeeLimit,testFoundationAddress,testFoundationKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> ex = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    String hex = "41" + ByteArray.toHexString(ex.get().getContractResult(0).toByteArray())
        .substring(24);
    logger.info("Deploy Address : " + Base58.encode58Check(ByteArray.fromHexString(hex)));
    byte[] ownerAddress = ByteArray.fromHexString(hex);

    methedStr = "Stake(address,uint256)";
    argStr = "\"" + testExecutiveAddress + "\"," + 10_000_000;
    txid = PublicMethed.triggerContract(ownerAddress,methedStr,
        argStr,false,10_000_000,maxFeeLimit,
        testFoundationAddress, testFoundationKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ex = PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);

    Account ownerAccount = PublicMethed.queryAccount(ownerAddress,blockingStubFull);
    final Cded ownerCded = ownerAccount.getCded(0);

    methedStr = "SelfdestructTest(address)";
    argStr = "\"" + Base58.encode58Check(contractAddress) + "\"";
    txid = PublicMethed.triggerContract(ownerAddress,methedStr,argStr,false,
        0,maxFeeLimit,testFoundationAddress,testFoundationKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    methedStr = "deploy(bytes,uint256)";
    argStr = "\"" + code + "\"," + 1;
    txid = PublicMethed.triggerContract(contractAddress,methedStr,argStr,false,
        0,maxFeeLimit,testFoundationAddress,testFoundationKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    ownerAccount =  PublicMethed.queryAccount(ownerAddress,blockingStubFull);
    Assert.assertEquals(ownerAccount.getBalance(),0);
    Assert.assertEquals(ownerAccount.getCdedCount(),0);
    Assert.assertEquals(ownerAccount.getVotesCount(),0);

    Account targetAccount = PublicMethed.queryAccount(contractAddress,blockingStubFull);
    Cded targetCded = targetAccount.getCded(0);

    Assert.assertEquals(ownerCded.getExpireTime(),targetCded.getExpireTime());
    Assert.assertEquals(ownerCded.getCdedBalance(),targetCded.getCdedBalance());

  }

  @Test(enabled = false, description = "create2 -> stake -> suicide -> sendcoin to create2 Address")
  public void stackSuicideAndCreate2Test002() {
    String filePath = "src/test/resources/soliditycode/stackSuicide001.sol";
    String contractName = "testStakeSuicide";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();

    String methedStr = "deploy(bytes,uint256)";
    String argStr = "\"" + code + "\"," + 2;
    String txid = PublicMethed.triggerContract(contractAddress,methedStr,argStr,false,
        0,maxFeeLimit,testFoundationAddress,testFoundationKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Optional<TransactionInfo> ex = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    String hex = "41" + ByteArray.toHexString(ex.get().getContractResult(0).toByteArray())
        .substring(24);
    logger.info("Deploy Address : " + Base58.encode58Check(ByteArray.fromHexString(hex)));
    byte[] ownerAddress = ByteArray.fromHexString(hex);

    methedStr = "Stake(address,uint256)";
    argStr = "\"" + testExecutiveAddress + "\"," + 10_000_000;
    txid = PublicMethed.triggerContract(ownerAddress,methedStr,
        argStr,false,10_000_000,maxFeeLimit,
        testFoundationAddress, testFoundationKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ex = PublicMethed.getTransactionInfoById(txid,blockingStubFull);
    Assert.assertEquals(ex.get().getResult(), TransactionInfo.code.SUCESS);

    Account ownerAccount = PublicMethed.queryAccount(ownerAddress,blockingStubFull);
    final Cded ownerCded = ownerAccount.getCded(0);

    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] testAddress001 = ecKey1.getAddress();

    methedStr = "SelfdestructTest(address)";
    argStr = "\"" + Base58.encode58Check(testAddress001) + "\"";
    txid = PublicMethed.triggerContract(ownerAddress,methedStr,argStr,false,
        0,maxFeeLimit,testFoundationAddress,testFoundationKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    long sendcoin = 1;
    Assert.assertTrue(PublicMethed.sendcoin(ownerAddress,sendcoin,testFoundationAddress,
        testFoundationKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    ownerAccount =  PublicMethed.queryAccount(ownerAddress,blockingStubFull);
    Assert.assertEquals(ownerAccount.getBalance(),sendcoin);
    Assert.assertEquals(ownerAccount.getCdedCount(),0);
    Assert.assertEquals(ownerAccount.getVotesCount(),0);

    Account targetAccount = PublicMethed.queryAccount(testAddress001,blockingStubFull);
    Cded targetCded = targetAccount.getCded(0);

    Assert.assertEquals(ownerCded.getExpireTime(),targetCded.getExpireTime());
    Assert.assertEquals(ownerCded.getCdedBalance(),targetCded.getCdedBalance());
  }


}
