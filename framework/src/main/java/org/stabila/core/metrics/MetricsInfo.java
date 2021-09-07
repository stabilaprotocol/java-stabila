package org.stabila.core.metrics;

import lombok.Data;
import org.stabila.core.metrics.blockchain.BlockChainInfo;
import org.stabila.core.metrics.net.NetInfo;
import org.stabila.core.metrics.node.NodeInfo;

@Data
public class MetricsInfo {
  private long interval;
  private NodeInfo node;
  private BlockChainInfo blockchain;
  private NetInfo net;
}
