package org.stabila.core.net.messagehandler;

import org.stabila.core.exception.P2pException;
import org.stabila.core.net.message.StabilaMessage;
import org.stabila.core.net.peer.PeerConnection;

public interface StabilaMsgHandler {

  void processMessage(PeerConnection peer, StabilaMessage msg) throws P2pException;

}
