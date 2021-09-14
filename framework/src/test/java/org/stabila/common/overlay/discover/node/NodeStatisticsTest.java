package org.stabila.common.overlay.discover.node;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stabila.common.net.udp.message.UdpMessageTypeEnum;
import org.stabila.common.overlay.discover.node.statistics.MessageStatistics;
import org.stabila.common.overlay.discover.node.statistics.NodeStatistics;
import org.stabila.common.overlay.message.DisconnectMessage;
import org.stabila.common.overlay.message.PongMessage;
import org.stabila.common.utils.Sha256Hash;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.net.message.BlockMessage;
import org.stabila.core.net.message.ChainInventoryMessage;
import org.stabila.core.net.message.FetchInvDataMessage;
import org.stabila.core.net.message.InventoryMessage;
import org.stabila.core.net.message.MessageTypes;
import org.stabila.core.net.message.SyncBlockChainMessage;
import org.stabila.core.net.message.TransactionsMessage;
import org.stabila.protos.Protocol;

public class NodeStatisticsTest {

  private NodeStatistics nodeStatistics;

  @Before
  public void init() {
    this.nodeStatistics = new NodeStatistics();
  }

  @Test
  public void testNode() throws NoSuchFieldException, IllegalAccessException {
    Protocol.ReasonCode reasonCode = this.nodeStatistics.getDisconnectReason();
    Assert.assertEquals(Protocol.ReasonCode.UNKNOWN, reasonCode);

    boolean isReputationPenalized = this.nodeStatistics.isReputationPenalized();
    Assert.assertFalse(isReputationPenalized);

    this.nodeStatistics.setPredefined(true);
    Assert.assertTrue(this.nodeStatistics.isPredefined());

    this.nodeStatistics.setPersistedReputation(10000);
    this.nodeStatistics.nodeDisconnectedRemote(Protocol.ReasonCode.INCOMPATIBLE_VERSION);
    isReputationPenalized = this.nodeStatistics.isReputationPenalized();
    Assert.assertTrue(isReputationPenalized);

    Field field = this.nodeStatistics.getClass().getDeclaredField("firstDisconnectedTime");
    field.setAccessible(true);
    field.set(this.nodeStatistics, System.currentTimeMillis() - 60 * 60 * 1000L - 1);
    isReputationPenalized = this.nodeStatistics.isReputationPenalized();
    Assert.assertFalse(isReputationPenalized);
    reasonCode = this.nodeStatistics.getDisconnectReason();
    Assert.assertEquals(Protocol.ReasonCode.UNKNOWN, reasonCode);

    String str = this.nodeStatistics.toString();
    //System.out.println(str);
    Assert.assertNotNull(str);

    this.nodeStatistics.nodeIsHaveDataTransfer();
    this.nodeStatistics.resetTcpFlow();
    this.nodeStatistics.discoverMessageLatency.add(10);
    this.nodeStatistics.discoverMessageLatency.add(20);
    long avg = this.nodeStatistics.discoverMessageLatency.getAvg();
    Assert.assertEquals(15, avg);

  }

  @Test
  public void testMessage() {
    MessageStatistics statistics = this.nodeStatistics.messageStatistics;
    statistics.addUdpInMessage(UdpMessageTypeEnum.DISCOVER_FIND_NODE);
    statistics.addUdpOutMessage(UdpMessageTypeEnum.DISCOVER_NEIGHBORS);
    statistics.addUdpInMessage(UdpMessageTypeEnum.DISCOVER_NEIGHBORS);
    statistics.addUdpOutMessage(UdpMessageTypeEnum.DISCOVER_FIND_NODE);
    Assert.assertEquals(1, statistics.discoverInFindNode.getTotalCount());
    long inFindNodeCount = statistics.discoverInFindNode.getTotalCount();
    long outNeighbours = statistics.discoverOutNeighbours.getTotalCount();
    Assert.assertEquals(inFindNodeCount, outNeighbours);

    PongMessage pongMessage = new PongMessage(MessageTypes.P2P_PONG.asByte(), Hex.decode("C0"));
    pongMessage.getData();
    String pongStr = pongMessage.toString();
    Assert.assertNotNull(pongStr);
    statistics.addTcpInMessage(pongMessage);
    statistics.addTcpOutMessage(pongMessage);
    Assert.assertEquals(1, statistics.p2pInPong.getTotalCount());

    DisconnectMessage disconnectMessage = new DisconnectMessage(Protocol.ReasonCode.TOO_MANY_PEERS);
    Assert.assertEquals(Protocol.ReasonCode.TOO_MANY_PEERS, disconnectMessage.getReasonCode());
    statistics.addTcpInMessage(disconnectMessage);
    statistics.addTcpOutMessage(disconnectMessage);
    Assert.assertEquals(1, statistics.p2pOutDisconnect.getTotalCount());

    SyncBlockChainMessage syncBlockChainMessage = new SyncBlockChainMessage(new ArrayList<>());
    String syncBlockChainStr = syncBlockChainMessage.toString();
    Assert.assertNotNull(syncBlockChainStr);
    statistics.addTcpInMessage(syncBlockChainMessage);
    statistics.addTcpOutMessage(syncBlockChainMessage);
    Assert.assertEquals(1, statistics.stabilaInSyncBlockChain.getTotalCount());

    ChainInventoryMessage chainInventoryMessage = new ChainInventoryMessage(new ArrayList<>(), 0L);
    String chainInventoryMessageStr = chainInventoryMessage.toString();
    Assert.assertNotNull(chainInventoryMessageStr);
    statistics.addTcpInMessage(chainInventoryMessage);
    statistics.addTcpOutMessage(chainInventoryMessage);
    Assert.assertEquals(1, statistics.stabilaOutBlockChainInventory.getTotalCount());

    InventoryMessage invMsgStb =
        new InventoryMessage(new ArrayList<>(), Protocol.Inventory.InventoryType.STB);
    String inventoryMessageStr = invMsgStb.toString();
    Assert.assertNotNull(inventoryMessageStr);
    statistics.addTcpInMessage(invMsgStb);
    statistics.addTcpOutMessage(invMsgStb);
    InventoryMessage invMsgBlock =
        new InventoryMessage(new ArrayList<>(), Protocol.Inventory.InventoryType.BLOCK);
    MessageTypes invType = invMsgBlock.getInvMessageType();
    Assert.assertEquals(MessageTypes.BLOCK, invType);
    statistics.addTcpInMessage(invMsgBlock);
    statistics.addTcpOutMessage(invMsgBlock);
    Assert.assertEquals(1, statistics.stabilaInBlockInventory.getTotalCount());

    FetchInvDataMessage fetchInvDataStb =
        new FetchInvDataMessage(new ArrayList<>(), Protocol.Inventory.InventoryType.STB);
    statistics.addTcpInMessage(fetchInvDataStb);
    statistics.addTcpOutMessage(fetchInvDataStb);
    FetchInvDataMessage fetchInvDataBlock =
        new FetchInvDataMessage(new ArrayList<>(), Protocol.Inventory.InventoryType.BLOCK);
    statistics.addTcpInMessage(fetchInvDataBlock);
    statistics.addTcpOutMessage(fetchInvDataBlock);
    Assert.assertEquals(1, statistics.stabilaInStbFetchInvData.getTotalCount());

    TransactionsMessage transactionsMessage =
        new TransactionsMessage(new LinkedList<>());
    statistics.addTcpInMessage(transactionsMessage);
    statistics.addTcpOutMessage(transactionsMessage);
    Assert.assertEquals(1, statistics.stabilaInStbs.getTotalCount());

    BlockCapsule blockCapsule = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
        System.currentTimeMillis(), Sha256Hash.ZERO_HASH.getByteString());
    BlockMessage blockMessage = new BlockMessage(blockCapsule);
    statistics.addTcpInMessage(blockMessage);
    statistics.addTcpOutMessage(blockMessage);
    long inBlockCount = statistics.stabilaInBlock.getTotalCount();
    Assert.assertEquals(1, inBlockCount);
  }
}
