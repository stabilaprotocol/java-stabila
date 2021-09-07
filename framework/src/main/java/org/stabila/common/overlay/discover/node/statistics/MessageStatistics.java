package org.stabila.common.overlay.discover.node.statistics;

import lombok.extern.slf4j.Slf4j;
import org.stabila.common.net.udp.message.UdpMessageTypeEnum;
import org.stabila.common.overlay.message.Message;
import org.stabila.core.net.message.FetchInvDataMessage;
import org.stabila.core.net.message.InventoryMessage;
import org.stabila.core.net.message.MessageTypes;
import org.stabila.core.net.message.TransactionsMessage;

@Slf4j
public class MessageStatistics {

  //udp discovery
  public final MessageCount discoverInPing = new MessageCount();
  public final MessageCount discoverOutPing = new MessageCount();
  public final MessageCount discoverInPong = new MessageCount();
  public final MessageCount discoverOutPong = new MessageCount();
  public final MessageCount discoverInFindNode = new MessageCount();
  public final MessageCount discoverOutFindNode = new MessageCount();
  public final MessageCount discoverInNeighbours = new MessageCount();
  public final MessageCount discoverOutNeighbours = new MessageCount();

  //tcp p2p
  public final MessageCount p2pInHello = new MessageCount();
  public final MessageCount p2pOutHello = new MessageCount();
  public final MessageCount p2pInPing = new MessageCount();
  public final MessageCount p2pOutPing = new MessageCount();
  public final MessageCount p2pInPong = new MessageCount();
  public final MessageCount p2pOutPong = new MessageCount();
  public final MessageCount p2pInDisconnect = new MessageCount();
  public final MessageCount p2pOutDisconnect = new MessageCount();

  //tcp stabila
  public final MessageCount stabilaInMessage = new MessageCount();
  public final MessageCount stabilaOutMessage = new MessageCount();

  public final MessageCount stabilaInSyncBlockChain = new MessageCount();
  public final MessageCount stabilaOutSyncBlockChain = new MessageCount();
  public final MessageCount stabilaInBlockChainInventory = new MessageCount();
  public final MessageCount stabilaOutBlockChainInventory = new MessageCount();

  public final MessageCount stabilaInTrxInventory = new MessageCount();
  public final MessageCount stabilaOutTrxInventory = new MessageCount();
  public final MessageCount stabilaInTrxInventoryElement = new MessageCount();
  public final MessageCount stabilaOutTrxInventoryElement = new MessageCount();

  public final MessageCount stabilaInBlockInventory = new MessageCount();
  public final MessageCount stabilaOutBlockInventory = new MessageCount();
  public final MessageCount stabilaInBlockInventoryElement = new MessageCount();
  public final MessageCount stabilaOutBlockInventoryElement = new MessageCount();

  public final MessageCount stabilaInTrxFetchInvData = new MessageCount();
  public final MessageCount stabilaOutTrxFetchInvData = new MessageCount();
  public final MessageCount stabilaInTrxFetchInvDataElement = new MessageCount();
  public final MessageCount stabilaOutTrxFetchInvDataElement = new MessageCount();

  public final MessageCount stabilaInBlockFetchInvData = new MessageCount();
  public final MessageCount stabilaOutBlockFetchInvData = new MessageCount();
  public final MessageCount stabilaInBlockFetchInvDataElement = new MessageCount();
  public final MessageCount stabilaOutBlockFetchInvDataElement = new MessageCount();


  public final MessageCount stabilaInTrx = new MessageCount();
  public final MessageCount stabilaOutTrx = new MessageCount();
  public final MessageCount stabilaInTrxs = new MessageCount();
  public final MessageCount stabilaOutTrxs = new MessageCount();
  public final MessageCount stabilaInBlock = new MessageCount();
  public final MessageCount stabilaOutBlock = new MessageCount();
  public final MessageCount stabilaOutAdvBlock = new MessageCount();

  public void addUdpInMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, true);
  }

  public void addUdpOutMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, false);
  }

  public void addTcpInMessage(Message msg) {
    addTcpMessage(msg, true);
  }

  public void addTcpOutMessage(Message msg) {
    addTcpMessage(msg, false);
  }

  private void addUdpMessage(UdpMessageTypeEnum type, boolean flag) {
    switch (type) {
      case DISCOVER_PING:
        if (flag) {
          discoverInPing.add();
        } else {
          discoverOutPing.add();
        }
        break;
      case DISCOVER_PONG:
        if (flag) {
          discoverInPong.add();
        } else {
          discoverOutPong.add();
        }
        break;
      case DISCOVER_FIND_NODE:
        if (flag) {
          discoverInFindNode.add();
        } else {
          discoverOutFindNode.add();
        }
        break;
      case DISCOVER_NEIGHBORS:
        if (flag) {
          discoverInNeighbours.add();
        } else {
          discoverOutNeighbours.add();
        }
        break;
      default:
        break;
    }
  }

  private void addTcpMessage(Message msg, boolean flag) {

    if (flag) {
      stabilaInMessage.add();
    } else {
      stabilaOutMessage.add();
    }

    switch (msg.getType()) {
      case P2P_HELLO:
        if (flag) {
          p2pInHello.add();
        } else {
          p2pOutHello.add();
        }
        break;
      case P2P_PING:
        if (flag) {
          p2pInPing.add();
        } else {
          p2pOutPing.add();
        }
        break;
      case P2P_PONG:
        if (flag) {
          p2pInPong.add();
        } else {
          p2pOutPong.add();
        }
        break;
      case P2P_DISCONNECT:
        if (flag) {
          p2pInDisconnect.add();
        } else {
          p2pOutDisconnect.add();
        }
        break;
      case SYNC_BLOCK_CHAIN:
        if (flag) {
          stabilaInSyncBlockChain.add();
        } else {
          stabilaOutSyncBlockChain.add();
        }
        break;
      case BLOCK_CHAIN_INVENTORY:
        if (flag) {
          stabilaInBlockChainInventory.add();
        } else {
          stabilaOutBlockChainInventory.add();
        }
        break;
      case INVENTORY:
        InventoryMessage inventoryMessage = (InventoryMessage) msg;
        int inventorySize = inventoryMessage.getInventory().getIdsCount();
        messageProcess(inventoryMessage.getInvMessageType(),
                stabilaInTrxInventory,stabilaInTrxInventoryElement,stabilaInBlockInventory,
                stabilaInBlockInventoryElement,stabilaOutTrxInventory,stabilaOutTrxInventoryElement,
                stabilaOutBlockInventory,stabilaOutBlockInventoryElement,
                flag, inventorySize);
        break;
      case FETCH_INV_DATA:
        FetchInvDataMessage fetchInvDataMessage = (FetchInvDataMessage) msg;
        int fetchSize = fetchInvDataMessage.getInventory().getIdsCount();
        messageProcess(fetchInvDataMessage.getInvMessageType(),
                stabilaInTrxFetchInvData,stabilaInTrxFetchInvDataElement,stabilaInBlockFetchInvData,
                stabilaInBlockFetchInvDataElement,stabilaOutTrxFetchInvData,stabilaOutTrxFetchInvDataElement,
                stabilaOutBlockFetchInvData,stabilaOutBlockFetchInvDataElement,
                flag, fetchSize);
        break;
      case TRXS:
        TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
        if (flag) {
          stabilaInTrxs.add();
          stabilaInTrx.add(transactionsMessage.getTransactions().getTransactionsCount());
        } else {
          stabilaOutTrxs.add();
          stabilaOutTrx.add(transactionsMessage.getTransactions().getTransactionsCount());
        }
        break;
      case TRX:
        if (flag) {
          stabilaInMessage.add();
        } else {
          stabilaOutMessage.add();
        }
        break;
      case BLOCK:
        if (flag) {
          stabilaInBlock.add();
        }
        stabilaOutBlock.add();
        break;
      default:
        break;
    }
  }
  
  
  private void messageProcess(MessageTypes messageType,
                              MessageCount inTrx,
                              MessageCount inTrxEle,
                              MessageCount inBlock,
                              MessageCount inBlockEle,
                              MessageCount outTrx,
                              MessageCount outTrxEle,
                              MessageCount outBlock,
                              MessageCount outBlockEle,
                              boolean flag, int size) {
    if (flag) {
      if (messageType == MessageTypes.TRX) {
        inTrx.add();
        inTrxEle.add(size);
      } else {
        inBlock.add();
        inBlockEle.add(size);
      }
    } else {
      if (messageType == MessageTypes.TRX) {
        outTrx.add();
        outTrxEle.add(size);
      } else {
        outBlock.add();
        outBlockEle.add(size);
      }
    }
  }

}
