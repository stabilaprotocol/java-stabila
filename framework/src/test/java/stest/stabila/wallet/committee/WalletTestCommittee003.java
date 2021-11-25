package stest.stabila.wallet.committee;

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
import org.stabila.core.Wallet;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.Base58;
import stest.stabila.wallet.common.client.utils.PublicMethed;


@Slf4j
public class WalletTestCommittee003 {

  private static final long now = System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  private final String executiveKey001 = Configuration.getByPath("testng.conf")
      .getString("executive.key1");
  //Executive 47.93.33.201
  private final String executiveKey002 = Configuration.getByPath("testng.conf")
      .getString("executive.key2");
  //Executive 123.56.10.6
  private final String executiveKey003 = Configuration.getByPath("testng.conf")
      .getString("executive.key3");
  //Wtiness 39.107.80.135
  private final String executiveKey004 = Configuration.getByPath("testng.conf")
      .getString("executive.key4");
  //Executive 47.93.184.2
  private final String executiveKey005 = Configuration.getByPath("testng.conf")
      .getString("executive.key5");
  private final byte[] executive001Address = PublicMethed.getFinalAddress(executiveKey001);
  //private final byte[] executive003Address = PublicMethed.getFinalAddress(executiveKey003);
  //private final byte[] executive004Address = PublicMethed.getFinalAddress(executiveKey004);
  //private final byte[] executive005Address = PublicMethed.getFinalAddress(executiveKey005);
  private final byte[] executive002Address = PublicMethed.getFinalAddress(executiveKey002);
  private ManagedChannel channelFull = null;
  private ManagedChannel channelSolidity = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
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
  public void testApproveProposal() {
    PublicMethed.sendcoin(executive001Address, 1000000L,
        toAddress, testKey003, blockingStubFull);
    PublicMethed.sendcoin(executive002Address, 1000000L,
        toAddress, testKey003, blockingStubFull);

    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(0L, 81000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Get proposal list
    ProposalList proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    Optional<ProposalList> listProposals = Optional.ofNullable(proposalList);
    final Integer proposalId = listProposals.get().getProposalsCount();
    logger.info(Integer.toString(proposalId));

    Assert.assertTrue(PublicMethed.approveProposal(executive002Address, executiveKey002, proposalId,
        true, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Get proposal list after approve
    proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    listProposals = Optional.ofNullable(proposalList);
    logger.info(Integer.toString(listProposals.get().getProposals(0).getApprovalsCount()));
    Assert.assertTrue(listProposals.get().getProposals(0).getApprovalsCount() == 1);
    //logger.info(Base58.encode58Check(executive002Address));
    //logger.info(Base58.encode58Check(listProposals.get().getProposals(0).
    // getApprovalsList().get(0).toByteArray()));
    Assert.assertTrue(Base58.encode58Check(executive002Address).equals(Base58.encode58Check(
        listProposals.get().getProposals(0).getApprovalsList().get(0).toByteArray())));

    //Failed to approve proposal when you already approval this proposal
    Assert.assertFalse(PublicMethed.approveProposal(executive002Address, executiveKey002, proposalId,
        true, blockingStubFull));

    //Success to change the option from true to false.
    Assert.assertTrue(PublicMethed.approveProposal(executive002Address, executiveKey002, proposalId,
        false, blockingStubFull));
    proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    listProposals = Optional.ofNullable(proposalList);
    Assert.assertTrue(listProposals.get().getProposals(0).getApprovalsCount() == 0);

    //Failed to approvel proposal when you already approval this proposal
    Assert.assertFalse(PublicMethed.approveProposal(executive002Address, executiveKey002, proposalId,
        false, blockingStubFull));

    //Non executive can't approval proposal
    Assert.assertFalse(PublicMethed.approveProposal(toAddress, testKey003, proposalId,
        true, blockingStubFull));

    //Muti approval
    Assert.assertTrue(PublicMethed.approveProposal(executive001Address, executiveKey001, proposalId,
        true, blockingStubFull));
    Assert.assertTrue(PublicMethed.approveProposal(executive002Address, executiveKey002, proposalId,
        true, blockingStubFull));
    //Assert.assertTrue(PublicMethed.approveProposal(executive003Address,executiveKey003,proposalId,
    //    true,blockingStubFull));
    //Assert.assertTrue(PublicMethed.approveProposal(executive004Address,executiveKey004,proposalId,
    //    true,blockingStubFull));
    //Assert.assertTrue(PublicMethed.approveProposal(executive005Address,executiveKey005,proposalId,
    //    true,blockingStubFull));
    proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    listProposals = Optional.ofNullable(proposalList);
    Assert.assertTrue(listProposals.get().getProposals(0).getApprovalsCount() == 2);


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


