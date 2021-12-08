package stest.stabila.wallet.dailybuild.multisign;

import static org.hamcrest.core.StringContains.containsString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI;
import org.stabila.api.GrpcAPI.Return;
import org.stabila.api.GrpcAPI.TransactionSignWeight;
import org.stabila.api.WalletGrpc;
import org.stabila.api.WalletSolidityGrpc;
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
public class MultiSign26 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);

  private final String testExecutives = Configuration.getByPath("testng.conf")
      .getString("executive.key1");
  private final byte[] executivesKey = PublicMethed.getFinalAddress(testExecutives);
  private ManagedChannel channelFull = null;
  private ManagedChannel searchChannelFull = null;

  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidityInFullnode = null;

  private WalletGrpc.WalletBlockingStub searchBlockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  private ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] test001Address = ecKey1.getAddress();
  private String dev001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

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


  @Test(enabled = true, description = "Sendcoin,use acticve address to sign,"
      + "not meet the requirements.Delete the address,broadcastTransaction.")
  public void testMultiUpdatepermissions_BeforeSign() {
    ECKey ecKey = new ECKey(Utils.getRandom());
    test001Address = ecKey.getAddress();
    long amount = 2 * updateAccountPermissionFee + 1;

    Assert.assertTrue(PublicMethed
        .sendcoin(test001Address, amount, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    dev001Key = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    final long balance = test001AddressAccount.getBalance();

    String[] permissionKeyString = new String[1];
    permissionKeyString[0] = dev001Key;

    String accountPermissionJson1 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0100000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";

    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson1, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount1 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    long balance1 = test001AddressAccount1.getBalance();
    Assert.assertEquals(balance - balance1, updateAccountPermissionFee);
    Transaction transaction = PublicMethedForMutiSign
        .sendcoinWithPermissionIdNotSign(fromAddress, 1, test001Address, 2, dev001Key,
            blockingStubFull);
    Transaction transaction1 = PublicMethed
        .addTransactionSign(transaction, sendAccountKey2, blockingStubFull);
    TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("Permission denied"));
    logger.info("transactionSignWeight:" + transactionSignWeight);
    Account test001AddressAccount2 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    long balance2 = test001AddressAccount2.getBalance();
    Assert.assertEquals(balance1, balance2);
    String accountPermissionJson2 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0100000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey4)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";
    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson2, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));

    Account test001AddressAccount3 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList3 = test001AddressAccount3.getActivePermissionList();
    Permission ownerPermission3 = test001AddressAccount3.getOwnerPermission();
    Permission executivePermission3 = test001AddressAccount3.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList3);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission3));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission3));

    final Return returnResult = PublicMethedForMutiSign
        .broadcastTransaction1(transaction1, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    long balance3 = test001AddressAccount3.getBalance();
    Assert.assertEquals(balance2 - balance3, updateAccountPermissionFee);
    logger.info("returnResult:");
    Assert
        .assertThat(returnResult.getCode().toString(), containsString("SIGERROR"));
    Assert
        .assertThat(returnResult.getMessage().toStringUtf8(),
            containsString("Permission denied"));


  }

  @Test(enabled = true, description = "Sendcoin,use acticve address to sign,"
      + "meet the all requirements.Delete the address,broadcastTransaction.")
  public void testMultiUpdatepermissions_BeforeSign_1() {
    ECKey ecKey = new ECKey(Utils.getRandom());
    test001Address = ecKey.getAddress();
    long amount = 2 * updateAccountPermissionFee + 1;

    Assert.assertTrue(PublicMethed
        .sendcoin(test001Address, amount, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    dev001Key = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    final long balance = test001AddressAccount.getBalance();

    String[] permissionKeyString = new String[1];
    permissionKeyString[0] = dev001Key;

    String accountPermissionJson1 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";

    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson1, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount1 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    long balance1 = test001AddressAccount1.getBalance();
    Assert.assertEquals(balance - balance1, updateAccountPermissionFee);
    Transaction transaction = PublicMethedForMutiSign
        .sendcoinWithPermissionIdNotSign(fromAddress, 1, test001Address, 2, dev001Key,
            blockingStubFull);
    final Transaction transaction1 = PublicMethed
        .addTransactionSign(transaction, sendAccountKey2, blockingStubFull);

    GrpcAPI.TransactionApprovedList transactionApprovedList = PublicMethed
            .getTransactionApprovedList(transaction, blockingStubFull);

    logger.info("transactionApprovedList:" + transactionApprovedList);
    Assert.assertEquals(0, transactionApprovedList.getApprovedListCount());
    Assert.assertEquals(2,
            transactionApprovedList.getTransaction().getTransaction().getRawData().getContract(0)
                    .getPermissionId());

    String accountPermissionJson2 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey4)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";
    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson2, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("but it is not contained of permission"));
    final Return returnResult = PublicMethedForMutiSign
        .broadcastTransaction1(transaction1, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account test001AddressAccount2 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList2 = test001AddressAccount2.getActivePermissionList();
    Permission ownerPermission2 = test001AddressAccount2.getOwnerPermission();
    Permission executivePermission2 = test001AddressAccount2.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList2);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission2));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission2));
    Assert
        .assertThat(returnResult.getCode().toString(), containsString("SIGERROR"));
    Assert
        .assertThat(returnResult.getMessage().toStringUtf8(),
            containsString("but it is not contained of permission"));

    long balance2 = test001AddressAccount2.getBalance();
    Assert.assertEquals(balance1 - balance2, updateAccountPermissionFee);
  }


  @Test(enabled = true, description = "Sendcoin,use owner address to sign,"
      + "Delete the address,broadcastTransaction.")
  public void testMultiUpdatepermissions_BeforeSign_2() {
    ECKey ecKey = new ECKey(Utils.getRandom());
    test001Address = ecKey.getAddress();
    long amount = 2 * updateAccountPermissionFee + 1;

    Assert.assertTrue(PublicMethed
        .sendcoin(test001Address, amount, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    dev001Key = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    final long balance = test001AddressAccount.getBalance();

    String[] permissionKeyString = new String[2];
    permissionKeyString[0] = dev001Key;
    permissionKeyString[1] = sendAccountKey2;

    String accountPermissionJson1 =
        "{\"owner_permission\":{\"type\":0,\"permission_name"
            + "\":\"owner\",\"threshold\":1,\"keys\":[{"
            + "\"address\":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":\"active0\","
            + "\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";

    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson1, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount1 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    long balance1 = test001AddressAccount1.getBalance();
    Assert.assertEquals(balance - balance1, updateAccountPermissionFee);
    Transaction transaction = PublicMethedForMutiSign
        .sendcoinWithPermissionIdNotSign(fromAddress, 1, test001Address, 0, dev001Key,
            blockingStubFull);
    final Transaction transaction1 = PublicMethed
        .addTransactionSign(transaction, sendAccountKey2, blockingStubFull);

    String accountPermissionJson2 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address"
            + "\":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":\""
            + "active0\",\"threshold\":1,\"operations\":"
            + "\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";
    String[] permissionKeyString1 = new String[1];
    permissionKeyString1[0] = dev001Key;
    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson2, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString1));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount2 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList2 = test001AddressAccount2.getActivePermissionList();
    Permission ownerPermission2 = test001AddressAccount2.getOwnerPermission();
    final Permission executivePermission2 = test001AddressAccount2.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList2);
    long balance2 = test001AddressAccount2.getBalance();
    Assert.assertEquals(balance1 - balance2, updateAccountPermissionFee);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission2));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission2));
    TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);
    logger.info("transactionSignWeight:" + transactionSignWeight);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("but it is not contained of permission"));
    Return returnResult = PublicMethedForMutiSign
        .broadcastTransaction1(transaction1, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert
        .assertThat(returnResult.getCode().toString(), containsString("SIGERROR"));
    Assert
        .assertThat(returnResult.getMessage().toStringUtf8(),
            containsString("but it is not contained of permission"));
    Account test001AddressAccount3 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    long balance3 = test001AddressAccount3.getBalance();
    Assert.assertEquals(balance2 - balance3, 0);
  }

  @Test(enabled = true, description = "AccountPermissionUpdate transaction,"
      + "use owner address to sign,Delete the address,broadcastTransaction.")
  public void testMultiUpdatepermissions_BeforeSign_3() {
    ECKey ecKey = new ECKey(Utils.getRandom());
    test001Address = ecKey.getAddress();
    long amount = 2 * updateAccountPermissionFee + 1;

    Assert.assertTrue(PublicMethed
        .sendcoin(test001Address, amount, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    dev001Key = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    final long balance = test001AddressAccount.getBalance();

    String[] permissionKeyString = new String[2];
    permissionKeyString[0] = dev001Key;
    permissionKeyString[1] = sendAccountKey2;

    String accountPermissionJson1 =
        "{\"owner_permission\":{\"type\":0,\"permission_name"
            + "\":\"owner\",\"threshold\":1,\"keys\":[{"
            + "\"address\":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":\"active0\","
            + "\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";

    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson1, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount1 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    long balance1 = test001AddressAccount1.getBalance();
    Assert.assertEquals(balance - balance1, updateAccountPermissionFee);
    String accountPermissionJson3 =
        "{\"owner_permission\":{\"type\":0,\"permission_name"
            + "\":\"owner\",\"threshold\":1,\"keys\":[{"
            + "\"address\":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":\"active0\","
            + "\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey4)
            + "\",\"weight\":1}]}]} ";

    Transaction transaction = PublicMethedForMutiSign
        .accountPermissionUpdateWithoutSign(accountPermissionJson3, test001Address, dev001Key,
            blockingStubFull,
            permissionKeyString);
    final Transaction transaction1 = PublicMethedForMutiSign
        .addTransactionSignWithPermissionId(transaction, sendAccountKey2, 0, blockingStubFull);
    String accountPermissionJson2 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address"
            + "\":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":\""
            + "active0\",\"threshold\":1,\"operations\":"
            + "\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";
    String[] permissionKeyString1 = new String[1];
    permissionKeyString1[0] = dev001Key;
    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson2, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString1));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount2 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList2 = test001AddressAccount2.getActivePermissionList();
    Permission ownerPermission2 = test001AddressAccount2.getOwnerPermission();
    Permission executivePermission2 = test001AddressAccount2.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList2);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission2));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission2));
    long balance2 = test001AddressAccount2.getBalance();
    Assert.assertEquals(balance1 - balance2, updateAccountPermissionFee);
    TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);
    logger.info("transactionSignWeight:" + transactionSignWeight);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("but it is not contained of permission"));
    Return returnResult = PublicMethedForMutiSign
        .broadcastTransaction1(transaction1, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Assert
        .assertThat(returnResult.getCode().toString(), containsString("SIGERROR"));
    Assert
        .assertThat(returnResult.getMessage().toStringUtf8(),
            containsString("but it is not contained of permission"));

    Account test001AddressAccount3 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    long balance3 = test001AddressAccount3.getBalance();
    Assert.assertEquals(balance2 - balance3, 0);
  }




  @Test(enabled = true, description = "Sendcoin,Delete the owner address,"
      + "use the address to sign,broadcastTransaction.")
  public void testMultiUpdatepermissions_AfterSign() {
    ECKey ecKey = new ECKey(Utils.getRandom());
    test001Address = ecKey.getAddress();
    long amount = 2 * updateAccountPermissionFee + 1;

    Assert.assertTrue(PublicMethed
        .sendcoin(test001Address, amount, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    dev001Key = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    final long balance = test001AddressAccount.getBalance();

    String[] permissionKeyString = new String[1];
    permissionKeyString[0] = dev001Key;

    String accountPermissionJson1 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";

    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson1, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount1 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    long balance1 = test001AddressAccount1.getBalance();
    Assert.assertEquals(balance - balance1, updateAccountPermissionFee);
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));
    logger.info("1-----------------------");

    final Transaction transaction = PublicMethedForMutiSign
        .sendcoinWithPermissionIdNotSign(fromAddress, 1, test001Address, 0, dev001Key,
            blockingStubFull);

    String accountPermissionJson2 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(sendAccountKey2) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey4)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";
    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson2, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount2 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList2 = test001AddressAccount2.getActivePermissionList();
    Permission ownerPermission2 = test001AddressAccount2.getOwnerPermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList2);
    long balance2 = test001AddressAccount2.getBalance();
    Assert.assertEquals(balance1 - balance2, updateAccountPermissionFee);
    Permission executivePermission2 = test001AddressAccount2.getExecutivePermission();
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission2));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission2));

    Transaction transaction1 = PublicMethed
        .addTransactionSign(transaction, dev001Key, blockingStubFull);
    TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("but it is not contained of permission"));
    Assert
        .assertFalse(PublicMethedForMutiSign.broadcastTransaction(transaction1, blockingStubFull));
    Account test001AddressAccount3 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    long balance3 = test001AddressAccount3.getBalance();
    Assert.assertEquals(balance2 - balance3, 0);

  }

  @Test(enabled = true, description = "AccountPermissionUpdate transaction,"
      + "Delete the owner address,use the address to sign,broadcastTransaction.")
  public void testMultiUpdatepermissions_AfterSign_4() {
    ECKey ecKey = new ECKey(Utils.getRandom());
    test001Address = ecKey.getAddress();
    long amount = 3 * updateAccountPermissionFee + 1;

    Assert.assertTrue(PublicMethed
        .sendcoin(test001Address, amount, fromAddress, testKey002,
            blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList = test001AddressAccount.getActivePermissionList();
    Permission ownerPermission = test001AddressAccount.getOwnerPermission();
    Permission executivePermission = test001AddressAccount.getExecutivePermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList);
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission));
    dev001Key = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    final long balance = test001AddressAccount.getBalance();

    String[] permissionKeyString = new String[1];
    permissionKeyString[0] = dev001Key;

    String accountPermissionJson1 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";

    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson1, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Account test001AddressAccount1 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList1 = test001AddressAccount1.getActivePermissionList();
    Permission ownerPermission1 = test001AddressAccount1.getOwnerPermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList1);
    long balance1 = test001AddressAccount1.getBalance();
    Assert.assertEquals(balance - balance1, updateAccountPermissionFee);
    Permission executivePermission1 = test001AddressAccount1.getExecutivePermission();
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission1));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission1));

    String accountPermissionJson3 =
        "{\"owner_permission\":{\"type\":0,\"permission_name"
            + "\":\"owner\",\"threshold\":1,\"keys\":[{"
            + "\"address\":\"" + PublicMethed.getAddressString(dev001Key) + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey2)
            + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":\"active0\","
            + "\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey4)
            + "\",\"weight\":1}]}]} ";

    final Transaction transaction = PublicMethedForMutiSign
        .accountPermissionUpdateWithoutSign(accountPermissionJson3, test001Address, dev001Key,
            blockingStubFull,
            permissionKeyString);

    String accountPermissionJson2 =
        "{\"owner_permission\":{\"type\":0,\"permission_name\""
            + ":\"owner\",\"threshold\":1,\"keys\":[{\"address\""
            + ":\"" + PublicMethed.getAddressString(sendAccountKey2) + "\",\"weight\":1}]},"
            + "\"active_permissions\":[{\"type\":2,\"permission_name\":"
            + "\"active0\",\"threshold\":1,\"operations\""
            + ":\"0200000000000000000000000000000000000000000000000000000000000000\","
            + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey4)
            + "\",\"weight\":1},"
            + "{\"address\":\"" + PublicMethed.getAddressString(sendAccountKey3)
            + "\",\"weight\":1}]}]} ";
    Assert.assertTrue(PublicMethedForMutiSign
        .accountPermissionUpdateWithPermissionId(accountPermissionJson2, test001Address, dev001Key,
            blockingStubFull, 0,
            permissionKeyString));

    Account test001AddressAccount2 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    List<Permission> permissionsList2 = test001AddressAccount2.getActivePermissionList();
    Permission ownerPermission2 = test001AddressAccount2.getOwnerPermission();
    PublicMethedForMutiSign.printPermissionList(permissionsList2);
    long balance2 = test001AddressAccount2.getBalance();
    Assert.assertEquals(balance1 - balance2, updateAccountPermissionFee);
    Permission executivePermission2 = test001AddressAccount2.getExecutivePermission();
    logger.info(PublicMethedForMutiSign.printPermission(ownerPermission2));
    logger.info(PublicMethedForMutiSign.printPermission(executivePermission2));

    Transaction transaction1 = PublicMethedForMutiSign
        .addTransactionSignWithPermissionId(transaction, dev001Key, 0, blockingStubFull);
    TransactionSignWeight transactionSignWeight = PublicMethedForMutiSign
        .getTransactionSignWeight(transaction1, blockingStubFull);
    Assert
        .assertThat(transactionSignWeight.getResult().getCode().toString(),
            containsString("PERMISSION_ERROR"));
    Assert
        .assertThat(transactionSignWeight.getResult().getMessage(),
            containsString("but it is not contained of permission"));
    Assert
        .assertFalse(PublicMethedForMutiSign.broadcastTransaction(transaction1, blockingStubFull));

    Account test001AddressAccount3 = PublicMethed.queryAccount(test001Address, blockingStubFull);
    long balance3 = test001AddressAccount3.getBalance();
    Assert.assertEquals(balance2 - balance3, 0);
  }

  @AfterMethod
  public void aftertest() {
    PublicMethed.freedResource(test001Address, dev001Key, fromAddress, blockingStubFull);
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
