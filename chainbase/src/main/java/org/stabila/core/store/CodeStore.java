package org.stabila.core.store;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stabila.core.db.TronStoreWithRevoking;
import org.stabila.core.capsule.CodeCapsule;

@Slf4j(topic = "DB")
@Component
public class CodeStore extends TronStoreWithRevoking<CodeCapsule> {

  @Autowired
  private CodeStore(@Value("code") String dbName) {
    super(dbName);
  }

  @Override
  public CodeCapsule get(byte[] key) {
    return getUnchecked(key);
  }

  public long getTotalCodes() {
    return Streams.stream(revokingDB.iterator()).count();
  }

  public byte[] findCodeByHash(byte[] hash) {
    return revokingDB.getUnchecked(hash);
  }
}
