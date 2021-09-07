package org.stabila.consensus.base;

import org.stabila.consensus.base.Param.Miner;
import org.stabila.core.capsule.BlockCapsule;

public interface BlockHandle {

  State getState();

  Object getLock();

  BlockCapsule produce(Miner miner, long blockTime, long timeout);

}