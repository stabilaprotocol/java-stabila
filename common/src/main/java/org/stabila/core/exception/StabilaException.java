package org.stabila.core.exception;

public class StabilaException extends Exception {

  public StabilaException() {
    super();
  }

  public StabilaException(String message) {
    super(message);
  }

  public StabilaException(String message, Throwable cause) {
    super(message, cause);
  }

}
