package org.stabila.core.db.common.iterator;

import java.util.Iterator;
import java.util.Map.Entry;
import org.stabila.core.capsule.ExecutiveCapsule;

public class ExecutiveIterator extends AbstractIterator<ExecutiveCapsule> {

  public ExecutiveIterator(Iterator<Entry<byte[], byte[]>> iterator) {
    super(iterator);
  }

  @Override
  protected ExecutiveCapsule of(byte[] value) {
    return new ExecutiveCapsule(value);
  }
}
