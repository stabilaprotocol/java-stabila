package stest.stabila.wallet.common.client;

import java.util.Comparator;
import org.stabila.protos.Protocol.Executive;

class ExecutiveComparator implements Comparator {

  public int compare(Object o1, Object o2) {
    return Long.compare(((Executive) o2).getVoteCount(), ((Executive) o1).getVoteCount());
  }
}