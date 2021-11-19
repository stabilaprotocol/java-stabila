package org.stabila.consensus.base;

import org.stabila.consensus.pbft.message.PbftBaseMessage;
import org.stabila.core.capsule.BlockCapsule;

public interface PbftInterface {

  boolean isSyncing();

  void forwardMessage(PbftBaseMessage message);

  BlockCapsule getBlock(long blockNum) throws Exception;

}