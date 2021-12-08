package stest.stabila.wallet.dailybuild.multisign;

import static org.hamcrest.core.StringContains.containsString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI.Return;
import org.stabila.api.GrpcAPI.TransactionSignWeight;
import org.stabila.api.WalletGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.protos.Protocol.Account;
import org.stabila.protos.Protocol.Permission;
import org.stabila.protos.Protocol.Transaction;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.utils.PublicMethed;
import stest.stabila.wallet.common.client.utils.PublicMethedForMutiSign;

@Slf4j
public class MultiSign34 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);

  private final String testExecutives = Configuration.getByPath("testng.conf")
      .getString("executive.key2");
  private final byte[] executivesKey = PublicMethed.getFinalAddress(testExecutives);
  private ManagedChannel channelFull = null;

  private WalletGrpc.WalletBlockingStub blockingStubFull = null;


  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);


  private ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] test002Address = ecKey2.getAddress();
  private String sendAccountKey2 = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

  private ECKey ecKey3 = new ECKey(Utils.getRandom());
  byte[] test003Address = ecKey3.getAddress();
  String sendAccountKey3 = ByteArray.toHexString(ecKey3.getPrivKeyBytes());
  private ECKey ecKey4 = new ECKey(Utils.getRandom());
  byte[] test004Address = ecKey4.getAddress();
  String sendAccountKey4 = ByteArray.toHexString(ecKey4.getPrivKeyBytes());
  private ECKey ecKey5 = new ECKey(Utils.getRandom());
  byte[] test005Address = ecKey5.getAddress();
  String sendAccountKey5 = ByteArray.toHexString(ecKey5.getPrivKeyBytes());
  private long multiSignFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.multiSignFee");
  private long updateAccountPermissionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.updateAccountPermissionFee");

  /**
   * constructor.
   */

  @BeforeClass
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);


  }


  @Test(enabled = true, description = "SR executive,sendcoin, use executivePermission address sign.")
  public void testMultiUpdatepermissions_42() {
    Assert.assertTrue(PublicMethed
        .sendcoin(executivesKey, 1000000000, fromAddress, testKey002,
            blockingStubFull));
    Account test001AddressAccount = PublicMethed.queryAccount(executivesKey, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    final long balance = test001AddressAccount.getBalance();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    Transaction transaction = PublicMethedForMutiSign
        .sendcoinWithPermissionIdNotSign(fromAddress, 1L, executivesKey, 1, testExecutives,
            blockingStubFull);
    Transaction transaction1 = PublicMethed
        .addTransactionSign(transaction, testExecutives, blockingStubFull);
    final TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);

    Account test001AddressAccount1 = PublicMethed.queryAccount(executivesKey, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    final long balance1 = test001AddressAccount1.getBalance();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    Assert.assertEquals(balance, balance1);
    logger.info("transaction:" + transactionSignWeight);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("Permission for this, does not exist!"));

    Return returnResult1 = PublicMethedForMutiSign
        .broadcastTransaction1(transaction1, blockingStubFull);
    logger.info("returnResult1:" + returnResult1);
    Assert
        .assertThat(returnResult1.getCode().toString(), containsString("SIGERROR"));
    Assert
        .assertThat(returnResult1.getMessage().toStringUtf8(),
            containsString("permission isn't exit"));

  }

  @Test(enabled = true, description = "SR executive,sendcoin, use active address sign.")
  public void testMultiUpdatepermissions_43() {
    Assert.assertTrue(PublicMethed
        .sendcoin(executivesKey, 1000000000, fromAddress, testKey002,
            blockingStubFull));
    Account test001AddressAccount = PublicMethed.queryAccount(executivesKey, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    final long balance = test001AddressAccount.getBalance();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    Transaction transaction = PublicMethedForMutiSign
        .sendcoinWithPermissionIdNotSign(fromAddress, 1L, executivesKey, 2, testExecutives,
            blockingStubFull);
    final Transaction transaction1 = PublicMethed
        .addTransactionSign(transaction, testExecutives, blockingStubFull);
    final TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);
    logger.info("transaction:" + transactionSignWeight);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("Permission for this, does not exist!"));
    Return returnResult1 = PublicMethedForMutiSign
        .broadcastTransaction1(transaction1, blockingStubFull);
    logger.info("returnResult1:" + returnResult1);
    Account test001AddressAccount1 = PublicMethed.queryAccount(executivesKey, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    final long balance1 = test001AddressAccount1.getBalance();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    Assert.assertEquals(balance, balance1);
    Assert
        .assertThat(returnResult1.getCode().toString(), containsString("SIGERROR"));
    Assert
        .assertThat(returnResult1.getMessage().toStringUtf8(),
            containsString("permission isn't exit"));

  }


  @Test(enabled = true, description = "SR executive,sendcoin, use owner address sign.")
  public void testMultiUpdatepermissions_44() {
    Assert.assertTrue(PublicMethed
        .sendcoin(executivesKey, 1000000000, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account test001AddressAccount = PublicMethed.queryAccount(executivesKey, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    final long balance = test001AddressAccount.getBalance();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info("balance: " + balance);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    Transaction transaction = PublicMethedForMutiSign
        .sendcoinWithPermissionIdNotSign(fromAddress, 1L, executivesKey, 0, testExecutives,
            blockingStubFull);
    final Transaction transaction1 = PublicMethed
        .addTransactionSign(transaction, testExecutives, blockingStubFull);
    Return returnResult1 = PublicMethedForMutiSign
        .broadcastTransaction1(transaction1, blockingStubFull);
    logger.info("returnResult1:" + returnResult1);
    Assert.assertTrue(returnResult1.getResult());
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account test001AddressAccount1 = PublicMethed.queryAccount(executivesKey, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    final long balance1 = test001AddressAccount1.getBalance();
    logger.info("balance1: " + balance1);
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    Assert.assertEquals(balance - balance1, 1);


  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

  }


}
