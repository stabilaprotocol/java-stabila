package org.tron.common.runtime;

import org.stabila.core.db.TransactionContext;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;


public interface Runtime {

  void execute(TransactionContext context)
      throws ContractValidateException, ContractExeException;

  ProgramResult getResult();

  String getRuntimeError();

}
