package org.stabila.core.db;

import lombok.Data;
import org.tron.common.runtime.ProgramResult;
import org.stabila.core.capsule.BlockCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.store.StoreFactory;

@Data
public class TransactionContext {

  private BlockCapsule blockCap;
  private TransactionCapsule trxCap;
  private StoreFactory storeFactory;
  private ProgramResult programResult = new ProgramResult();
  private boolean isStatic;
  private boolean eventPluginLoaded;

  public TransactionContext(BlockCapsule blockCap, TransactionCapsule trxCap,
      StoreFactory storeFactory,
      boolean isStatic,
      boolean eventPluginLoaded) {
    this.blockCap = blockCap;
    this.trxCap = trxCap;
    this.storeFactory = storeFactory;
    this.isStatic = isStatic;
    this.eventPluginLoaded = eventPluginLoaded;
  }
}
