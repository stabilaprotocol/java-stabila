package org.stabila.core.store;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stabila.core.db.StabilaStoreWithRevoking;
import org.stabila.core.capsule.ExecutiveCapsule;

@Slf4j(topic = "DB")
@Component
public class ExecutiveStore extends StabilaStoreWithRevoking<ExecutiveCapsule> {

  @Autowired
  protected ExecutiveStore(@Value("executive") String dbName) {
    super(dbName);
  }

  /**
   * get all executives.
   */
  public List<ExecutiveCapsule> getAllExecutives() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  @Override
  public ExecutiveCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new ExecutiveCapsule(value);
  }
}
