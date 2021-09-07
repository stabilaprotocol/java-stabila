package org.stabila.core.exception;

public class AccountResourceInsufficientException extends StabilaException {

  public AccountResourceInsufficientException() {
    super("Insufficient bandwidth and balance to create new account");
  }

  public AccountResourceInsufficientException(String message) {
    super(message);
  }
}

