package org.stabila.common.overlay.server;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.stabila.common.utils.Commons;
import org.stabila.core.ChainBaseManager;
import org.stabila.core.capsule.ExecutiveCapsule;
import org.stabila.core.net.peer.PeerConnection;

@Slf4j(topic = "net")
@Component
@Scope("prototype")
public class StabilaChannelInitializer extends ChannelInitializer<NioSocketChannel> {

  @Autowired
  private ApplicationContext ctx;

  @Autowired
  private ChannelManager channelManager;

  @Autowired
  private ChainBaseManager chainBaseManager;

  private String remoteId;

  private boolean peerDiscoveryMode = false;

  public StabilaChannelInitializer(String remoteId) {
    this.remoteId = remoteId;
  }

  @Override
  public void initChannel(NioSocketChannel ch) throws Exception {
    try {
      final Channel channel = ctx.getBean(PeerConnection.class);

      channel.init(ch.pipeline(), remoteId, peerDiscoveryMode, channelManager);

      // limit the size of receiving buffer to 1024
      ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
      ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
      ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);

      // be aware of channel closing
      ch.closeFuture().addListener((ChannelFutureListener) future -> {
        logger.info("Close channel:" + channel);
        String executiveAddress = channel.getNode().getExecutiveAddress();
        if (executiveAddress != null) {
          byte[] executiveAddressBytes = Commons.decodeFromBase58Check(executiveAddress);
          if (executiveAddressBytes != null) {
            ExecutiveCapsule executive = chainBaseManager.getExecutiveStore().get(executiveAddressBytes);
            if (executive != null) {
              executive.setAlive(false);
              chainBaseManager.getExecutiveStore().put(executiveAddressBytes, executive);
            }
          }
        }
        if (!peerDiscoveryMode) {
          channelManager.notifyDisconnect(channel);
        }
      });

    } catch (Exception e) {
      logger.error("Unexpected error: ", e);
    }
  }

  public void setPeerDiscoveryMode(boolean peerDiscoveryMode) {
    this.peerDiscoveryMode = peerDiscoveryMode;
  }
}
