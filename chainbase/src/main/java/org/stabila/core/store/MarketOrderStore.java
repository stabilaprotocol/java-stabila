package org.stabila.core.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stabila.core.db.TronStoreWithRevoking;
import org.stabila.core.capsule.MarketOrderCapsule;
import org.stabila.core.exception.ItemNotFoundException;

@Component
public class MarketOrderStore extends TronStoreWithRevoking<MarketOrderCapsule> {

  @Autowired
  protected MarketOrderStore(@Value("market_order") String dbName) {
    super(dbName);
  }

  @Override
  public MarketOrderCapsule get(byte[] key) throws ItemNotFoundException {
    byte[] value = revokingDB.get(key);
    return new MarketOrderCapsule(value);
  }

}