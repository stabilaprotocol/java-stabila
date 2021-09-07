package org.stabila.core.exception;

public class StabilaRuntimeException extends RuntimeException {

  public StabilaRuntimeException() {
    super();
  }

  public StabilaRuntimeException(String message) {
    super(message);
  }

  public StabilaRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public StabilaRuntimeException(Throwable cause) {
    super(cause);
  }

  protected StabilaRuntimeException(String message, Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }


}
