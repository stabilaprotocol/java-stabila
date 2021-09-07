package org.stabila.core.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.stabila.common.overlay.server.Channel;
import org.stabila.common.overlay.server.MessageQueue;
import org.stabila.core.net.message.StabilaMessage;
import org.stabila.core.net.peer.PeerConnection;

@Component
@Scope("prototype")
public class StabilaNetHandler extends SimpleChannelInboundHandler<StabilaMessage> {

  protected PeerConnection peer;

  private MessageQueue msgQueue;

  @Autowired
  private StabilaNetService tronNetService;

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, StabilaMessage msg) throws Exception {
    msgQueue.receivedMessage(msg);
    tronNetService.onMessage(peer, msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    peer.processException(cause);
  }

  public void setMsgQueue(MessageQueue msgQueue) {
    this.msgQueue = msgQueue;
  }

  public void setChannel(Channel channel) {
    this.peer = (PeerConnection) channel;
  }

}