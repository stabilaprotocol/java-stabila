package org.stabila.core.exception;

public class ContractValidateException extends StabilaException {

  public ContractValidateException() {
    super();
  }

  public ContractValidateException(String message) {
    super(message);
  }

  public ContractValidateException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
