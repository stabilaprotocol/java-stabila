package org.stabila.core.actuator;

import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;

public interface Actuator2 {

  void execute(Object object) throws ContractExeException;

  void validate(Object object) throws ContractValidateException;
}