package stest.stabila.wallet.committee;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.stabila.api.WalletGrpc;
import org.stabila.api.WalletSolidityGrpc;
import org.stabila.core.Wallet;
import stest.stabila.wallet.common.client.Configuration;
import stest.stabila.wallet.common.client.Parameter.CommonConstant;
import stest.stabila.wallet.common.client.utils.PublicMethed;


@Slf4j
public class WalletTestCommittee002 {

  private static final long now = System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
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
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
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
  }


  @Test(enabled = true)
  public void testCreateProposalMaintenanceTimeInterval() {
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
    Assert.assertTrue(PublicMethed.sendcoin(executive001Address, 10000000L,
        toAddress, testKey003, blockingStubFull));

    //0:MAINTENANCE_TIME_INTERVAL,[3*27s,24h]
    //Minimum interval
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(0L, 81000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum interval
    proposalMap.put(0L, 86400000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum -1 interval, create failed.
    proposalMap.put(0L, 80000L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 interval
    proposalMap.put(0L, 86401000L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(0L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003, proposalMap,
        blockingStubFull));
  }

  @Test(enabled = true)
  public void testCreateProposalAccountUpgradeCost() {
    //1:ACCOUNT_UPGRADE_COST,[0,100 000 000 000 000 000]//drop
    //Minimum AccountUpgradeCost
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(1L, 0L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum AccountUpgradeCost
    proposalMap.put(1L, 100000000000000000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum - 1 AccountUpgradeCost
    proposalMap.put(1L, -1L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 AccountUpgradeCost
    proposalMap.put(1L, 100000000000000001L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(1L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003,
        proposalMap, blockingStubFull));
  }

  @Test(enabled = true)
  public void testCreateProposalCreateAccountFee() {
    //2:CREATE_ACCOUNT_FEE,[0,100 000 000 000 000 000]//drop
    //Minimum CreateAccountFee
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(2L, 0L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum CreateAccountFee
    proposalMap.put(2L, 100000000000000000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum - 1 CreateAccountFee
    proposalMap.put(2L, -1L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 CreateAccountFee
    proposalMap.put(2L, 100000000000000001L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(2L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003,
        proposalMap, blockingStubFull));

  }

  @Test(enabled = true)
  public void testTransactionFee() {
    //3:TRANSACTION_FEE,[0,100 000 000 000 000 000]//drop
    //Minimum TransactionFee
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(3L, 0L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum TransactionFee
    proposalMap.put(3L, 100000000000000000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum - 1 TransactionFee
    proposalMap.put(3L, -1L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 TransactionFee
    proposalMap.put(3L, 100000000000000001L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(3L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003,
        proposalMap, blockingStubFull));

  }

  @Test(enabled = true)
  public void testAssetIssueFee() {
    //4:ASSET_ISSUE_FEE,[0,100 000 000 000 000 000]//drop
    //Minimum AssetIssueFee
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(4L, 0L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Duplicat proposals
    proposalMap.put(4L, 0L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum AssetIssueFee
    proposalMap.put(4L, 100000000000000000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum - 1 AssetIssueFee
    proposalMap.put(4L, -1L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 AssetIssueFee
    proposalMap.put(4L, 100000000000000001L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(4L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003,
        proposalMap, blockingStubFull));

  }

  @Test(enabled = true)
  public void testExecutivePayPerBlock() {
    //5:EXECUTIVE_PAY_PER_BLOCK,[0,100 000 000 000 000 000]//drop
    //Minimum ExecutivePayPerBlock
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(5L, 0L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum ExecutivePayPerBlock
    proposalMap.put(5L, 100000000000000000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum - 1 ExecutivePayPerBlock
    proposalMap.put(5L, -1L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 ExecutivePayPerBlock
    proposalMap.put(5L, 100000000000000001L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(5L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003,
        proposalMap, blockingStubFull));

  }

  @Test(enabled = true)
  public void testExecutiveStandbyAllowance() {
    //6:EXECUTIVE_STANDBY_ALLOWANCE,[0,100 000 000 000 000 000]//drop
    //Minimum ExecutiveStandbyAllowance
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(6L, 0L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum ExecutiveStandbyAllowance
    proposalMap.put(6L, 100000000000000000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum - 1 ExecutiveStandbyAllowance
    proposalMap.put(6L, -1L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 ExecutiveStandbyAllowance
    proposalMap.put(6L, 100000000000000001L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(6L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003,
        proposalMap, blockingStubFull));

  }

  @Test(enabled = true)
  public void testCreateNewAccountFeeInSystemControl() {
    //7:CREATE_NEW_ACCOUNT_FEE_IN_SYSTEM_CONTRACT,0 or 1
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(7L, 1L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum ExecutiveStandbyAllowance
    proposalMap.put(7L, 100000000000000000L);
    Assert.assertTrue(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Minimum - 1 ExecutiveStandbyAllowance
    proposalMap.put(6L, -1L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Maximum + 1 ExecutiveStandbyAllowance
    proposalMap.put(6L, 100000000000000001L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //Non executive account
    proposalMap.put(6L, 86400000L);
    Assert.assertFalse(PublicMethed.createProposal(toAddress, testKey003,
        proposalMap, blockingStubFull));

  }


  @Test(enabled = true)
  public void testInvalidProposals() {
    // The index isn't from 0-9
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(10L, 60L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));

    //The index is -1
    proposalMap.put(-1L, 6L);
    Assert.assertFalse(PublicMethed.createProposal(executive001Address, executiveKey001,
        proposalMap, blockingStubFull));


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


