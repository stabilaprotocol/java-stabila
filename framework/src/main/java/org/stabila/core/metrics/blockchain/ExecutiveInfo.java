package org.stabila.core.metrics.blockchain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExecutiveInfo {
  private String address;
  private int version;
}
