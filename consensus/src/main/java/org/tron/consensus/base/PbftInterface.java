package org.tron.consensus.base;

import org.tron.consensus.pbft.message.PbftBaseMessage;
import org.stabila.core.capsule.BlockCapsule;

public interface PbftInterface {

  boolean isSyncing();

  void forwardMessage(PbftBaseMessage message);

  BlockCapsule getBlock(long blockNum) throws Exception;

}