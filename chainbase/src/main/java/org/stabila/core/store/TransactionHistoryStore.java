package org.stabila.core.store;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stabila.core.db.TronStoreWithRevoking;
import org.tron.common.parameter.CommonParameter;
import org.stabila.core.capsule.TransactionInfoCapsule;
import org.stabila.core.exception.BadItemException;

@Component
public class TransactionHistoryStore extends TronStoreWithRevoking<TransactionInfoCapsule> {

  @Autowired
  public TransactionHistoryStore(@Value("transactionHistoryStore") String dbName) {
    super(dbName);
  }

  @Override
  public TransactionInfoCapsule get(byte[] key) throws BadItemException {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new TransactionInfoCapsule(value);
  }

  @Override
  public void put(byte[] key, TransactionInfoCapsule item) {
    if (BooleanUtils.toBoolean(CommonParameter.getInstance()
        .getStorage().getTransactionHistorySwitch())) {
      super.put(key, item);
    }
  }
}
