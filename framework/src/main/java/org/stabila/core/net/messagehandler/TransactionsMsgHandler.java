package org.stabila.core.net.messagehandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.stabila.core.config.args.Args;
import org.stabila.core.exception.P2pException;
import org.stabila.core.exception.P2pException.TypeEnum;
import org.stabila.core.net.StabilaNetDelegate;
import org.stabila.core.net.message.TransactionMessage;
import org.stabila.core.net.message.TransactionsMessage;
import org.stabila.core.net.message.StabilaMessage;
import org.stabila.core.net.peer.Item;
import org.stabila.core.net.peer.PeerConnection;
import org.stabila.core.net.service.AdvService;
import org.stabila.protos.Protocol.Inventory.InventoryType;
import org.stabila.protos.Protocol.ReasonCode;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract.ContractType;

@Slf4j(topic = "net")
@Component
public class TransactionsMsgHandler implements StabilaMsgHandler {

  private static int MAX_STB_SIZE = 50_000;
  private static int MAX_SMART_CONTRACT_SUBMIT_SIZE = 100;
  @Autowired
  private StabilaNetDelegate stabilaNetDelegate;
  @Autowired
  private AdvService advService;

  private BlockingQueue<StbEvent> smartContractQueue = new LinkedBlockingQueue(MAX_STB_SIZE);

  private BlockingQueue<Runnable> queue = new LinkedBlockingQueue();

  private int threadNum = Args.getInstance().getValidateSignThreadNum();
  private ExecutorService stbHandlePool = new ThreadPoolExecutor(threadNum, threadNum, 0L,
      TimeUnit.MILLISECONDS, queue);

  private ScheduledExecutorService smartContractExecutor = Executors
      .newSingleThreadScheduledExecutor();

  public void init() {
    handleSmartContract();
  }

  public void close() {
    smartContractExecutor.shutdown();
  }

  public boolean isBusy() {
    return queue.size() + smartContractQueue.size() > MAX_STB_SIZE;
  }

  @Override
  public void processMessage(PeerConnection peer, StabilaMessage msg) throws P2pException {
    TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
    check(peer, transactionsMessage);
    for (Transaction stb : transactionsMessage.getTransactions().getTransactionsList()) {
      int type = stb.getRawData().getContract(0).getType().getNumber();
      if (type == ContractType.TriggerSmartContract_VALUE
          || type == ContractType.CreateSmartContract_VALUE) {
        if (!smartContractQueue.offer(new StbEvent(peer, new TransactionMessage(stb)))) {
          logger.warn("Add smart contract failed, queueSize {}:{}", smartContractQueue.size(),
              queue.size());
        }
      } else {
        stbHandlePool.submit(() -> handleTransaction(peer, new TransactionMessage(stb)));
      }
    }
  }

  private void check(PeerConnection peer, TransactionsMessage msg) throws P2pException {
    for (Transaction stb : msg.getTransactions().getTransactionsList()) {
      Item item = new Item(new TransactionMessage(stb).getMessageId(), InventoryType.STB);
      if (!peer.getAdvInvRequest().containsKey(item)) {
        throw new P2pException(TypeEnum.BAD_MESSAGE,
            "stb: " + msg.getMessageId() + " without request.");
      }
      peer.getAdvInvRequest().remove(item);
    }
  }

  private void handleSmartContract() {
    smartContractExecutor.scheduleWithFixedDelay(() -> {
      try {
        while (queue.size() < MAX_SMART_CONTRACT_SUBMIT_SIZE) {
          StbEvent event = smartContractQueue.take();
          stbHandlePool.submit(() -> handleTransaction(event.getPeer(), event.getMsg()));
        }
      } catch (Exception e) {
        logger.error("Handle smart contract exception.", e);
      }
    }, 1000, 20, TimeUnit.MILLISECONDS);
  }

  private void handleTransaction(PeerConnection peer, TransactionMessage stb) {
    if (peer.isDisconnect()) {
      logger.warn("Drop stb {} from {}, peer is disconnect.", stb.getMessageId(),
          peer.getInetAddress());
      return;
    }

    if (advService.getMessage(new Item(stb.getMessageId(), InventoryType.STB)) != null) {
      return;
    }

    try {
      stabilaNetDelegate.pushTransaction(stb.getTransactionCapsule());
      advService.broadcast(stb);
    } catch (P2pException e) {
      logger.warn("Stb {} from peer {} process failed. type: {}, reason: {}",
          stb.getMessageId(), peer.getInetAddress(), e.getType(), e.getMessage());
      if (e.getType().equals(TypeEnum.BAD_STB)) {
        peer.disconnect(ReasonCode.BAD_TX);
      }
    } catch (Exception e) {
      logger.error("Stb {} from peer {} process failed.", stb.getMessageId(), peer.getInetAddress(),
          e);
    }
  }

  class StbEvent {

    @Getter
    private PeerConnection peer;
    @Getter
    private TransactionMessage msg;
    @Getter
    private long time;

    public StbEvent(PeerConnection peer, TransactionMessage msg) {
      this.peer = peer;
      this.msg = msg;
      this.time = System.currentTimeMillis();
    }
  }
}