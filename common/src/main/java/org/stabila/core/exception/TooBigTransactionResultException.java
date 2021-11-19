package org.stabila.core.exception;

public class TooBigTransactionResultException extends StabilaException {

  public TooBigTransactionResultException() {
    super("too big transaction result");
  }

  public TooBigTransactionResultException(String message) {
    super(message);
  }
}
