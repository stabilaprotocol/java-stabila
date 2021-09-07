package org.stabila.core.exception;

public class BadTransactionException extends StabilaException {

  public BadTransactionException() {
    super();
  }

  public BadTransactionException(String message) {
    super(message);
  }

  public BadTransactionException(String message, Throwable cause) {
    super(message, cause);
  }
}
