package stest.stabila.wallet.dailybuild.operationupdate;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.GrpcAPI.EmptyMessage;
import org.stabila.api.GrpcAPI.ProposalList;
import org.stabila.api.WalletGrpc;
import org.stabila.api.WalletSolidityGrpc;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.utils.ByteArray;
import org.stabila.common.utils.Utils;
import org.stabila.core.Wallet;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;
import stest.stabila.wallet.common.client.utils.PublicMethedForMutiSign;


@Slf4j
public class MutiSignProposalTest {

  private static final long now = System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String executiveKey001 = Configuration.getByPath("testng.conf")
      .getString("executive.key2");
  private final byte[] executive001Address = PublicMethed.getFinalAddress(executiveKey001);
  private final String operations = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.operations");
  String[] permissionKeyString = new String[2];
  String[] ownerKeyString = new String[2];
  String accountPermissionJson = "";
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] manager1Address = ecKey1.getAddress();
  String manager1Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] manager2Address = ecKey2.getAddress();
  String manager2Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private long multiSignFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.multiSignFee");
  private long updateAccountPermissionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.updateAccountPermissionFee");
  private ManagedChannel channelFull = null;
  private ManagedChannel channelSolidity = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
  }

  @Test(enabled = true)
  public void testMutiSignForProposal() {
    long needcoin = updateAccountPermissionFee + multiSignFee * 5;
    Assert.assertTrue(PublicMethed.sendcoin(executive001Address, needcoin + 10000000L,
        fromAddress, testKey002, blockingStubFull));

    ecKey1 = new ECKey(Utils.getRandom());
    manager1Address = ecKey1.getAddress();
    manager1Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

    ecKey2 = new ECKey(Utils.getRandom());
    manager2Address = ecKey2.getAddress();
    manager2Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long balanceBefore = PublicMethed.queryAccount(executive001Address, blockingStubFull)
        .getBalance();
    logger.info("balanceBefore: " + balanceBefore);

    permissionKeyString[0] = manager1Key;
    permissionKeyString[1] = manager2Key;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ownerKeyString[0] = executiveKey001;
    ownerKeyString[1] = testKey002;

    accountPermissionJson = "{\"owner_permission\":{\"type\":0,\"permission_name\":\"owner\""
        + ",\"threshold\":2,\"keys\":[{\"address\":\"" + PublicMethed
        .getAddressString(executiveKey001) + "\","
        + "\"weight\":1},{\"address\":\"" + PublicMethed.getAddressString(testKey002)
        + "\",\"weight\":1}]},"
        + "\"executive_permission\":{\"type\":1,\"permission_name\":\"owner\",\"threshold\":1,"
        + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(executiveKey001)
        + "\",\"weight\":1}]},"
        + "\"active_permissions\":[{\"type\":2,\"permission_name\":\"active0\",\"threshold\":2,"
        + "\"operations\":\"7fff1fc0037e0000000000000000000000000000000000000000000000000000\","
        + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(manager1Key)
        + "\",\"weight\":1},"
        + "{\"address\":\"" + PublicMethed.getAddressString(manager2Key) + "\",\"weight\":1}]}]} ";
    logger.info(accountPermissionJson);
    PublicMethedForMutiSign.accountPermissionUpdate(
        accountPermissionJson, executive001Address, executiveKey001,
        blockingStubFull, ownerKeyString);

    //Create a proposal

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(0L, 81000L);
    Assert.assertTrue(
        PublicMethedForMutiSign.createProposalWithPermissionId(executive001Address, executiveKey001,
            proposalMap, 0, blockingStubFull, ownerKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Get proposal list
    ProposalList proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    Optional<ProposalList> listProposals = Optional.ofNullable(proposalList);
    final Integer proposalId = listProposals.get().getProposalsCount();
    logger.info(Integer.toString(proposalId));

    Assert.assertTrue(PublicMethedForMutiSign.approveProposalWithPermission(
        executive001Address, executiveKey001, proposalId,
        true, 0, blockingStubFull, ownerKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Delete proposal list after approve
    Assert.assertTrue(PublicMethedForMutiSign.deleteProposalWithPermissionId(
        executive001Address, executiveKey001, proposalId, 0, blockingStubFull, ownerKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long balanceAfter = PublicMethed.queryAccount(executive001Address, blockingStubFull)
        .getBalance();
    logger.info("balanceAfter: " + balanceAfter);

  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


