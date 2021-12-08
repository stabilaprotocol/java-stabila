package org.stabila.core.metrics.blockchain;

import lombok.Data;

@Data
public class DupExecutiveInfo {
  private String address;
  private long blockNum;
  private int count;
}
