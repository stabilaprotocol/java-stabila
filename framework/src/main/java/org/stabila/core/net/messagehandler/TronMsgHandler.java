package org.stabila.core.net.messagehandler;

import org.stabila.core.net.message.TronMessage;
import org.stabila.core.net.peer.PeerConnection;
import org.stabila.core.exception.P2pException;

public interface TronMsgHandler {

  void processMessage(PeerConnection peer, TronMessage msg) throws P2pException;

}
