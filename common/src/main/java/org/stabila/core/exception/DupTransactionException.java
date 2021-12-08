package org.stabila.core.exception;

public class DupTransactionException extends StabilaException {

  public DupTransactionException() {
    super();
  }

  public DupTransactionException(String message) {
    super(message);
  }
}
