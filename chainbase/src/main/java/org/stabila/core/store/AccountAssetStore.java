package org.stabila.core.store;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stabila.core.db.StabilaStoreWithRevoking;
import org.stabila.core.capsule.AccountAssetCapsule;

@Slf4j(topic = "DB")
@Component
public class AccountAssetStore extends StabilaStoreWithRevoking<AccountAssetCapsule> {

  @Autowired
  protected AccountAssetStore(@Value("account-asset-issue") String dbName) {
    super(dbName);
  }

  @Override
  public AccountAssetCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new AccountAssetCapsule(value);
  }
}
