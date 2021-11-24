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
import org.stabila.api.GrpcAPI.PaginatedMessage;
import org.stabila.api.GrpcAPI.ProposalList;
import org.stabila.api.WalletGrpc;
import org.stabila.api.WalletSolidityGrpc;
import org.stabila.core.Wallet;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;


@Slf4j
public class WalletTestCommittee001 {

  private static final long now = System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  //Executive 47.93.9.236
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
  private final byte[] executive002Address = PublicMethed.getFinalAddress(executiveKey002);
  private final byte[] executive003Address = PublicMethed.getFinalAddress(executiveKey003);
  private final byte[] executive004Address = PublicMethed.getFinalAddress(executiveKey004);
  private final byte[] executive005Address = PublicMethed.getFinalAddress(executiveKey005);
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


  @Test
  public void testListProposals() {
    //List proposals
    ProposalList proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    Optional<ProposalList> listProposals = Optional.ofNullable(proposalList);
    final Integer beforeProposalCount = listProposals.get().getProposalsCount();

    //CreateProposal
    final long now = System.currentTimeMillis();
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(0L, 1000000L);
    PublicMethed.createProposal(executive001Address, executiveKey001, proposalMap, blockingStubFull);

    //List proposals
    proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    listProposals = Optional.ofNullable(proposalList);
    Integer afterProposalCount = listProposals.get().getProposalsCount();
    Assert.assertTrue(beforeProposalCount + 1 == afterProposalCount);
    logger.info(Long.toString(listProposals.get().getProposals(0).getCreateTime()));
    logger.info(Long.toString(now));
    //Assert.assertTrue(listProposals.get().getProposals(0).getCreateTime() >= now);
    Assert.assertTrue(listProposals.get().getProposals(0).getParametersMap().equals(proposalMap));

    //getProposalListPaginated
    PaginatedMessage.Builder pageMessageBuilder = PaginatedMessage.newBuilder();
    pageMessageBuilder.setOffset(0);
    pageMessageBuilder.setLimit(1);
    ProposalList paginatedProposalList = blockingStubFull
        .getPaginatedProposalList(pageMessageBuilder.build());
    Assert.assertTrue(paginatedProposalList.getProposalsCount() >= 1);
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


