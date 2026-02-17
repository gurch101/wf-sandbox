package com.gurch.sandbox.idempotency;

/** Exception thrown when an idempotency key is reused with a different payload. */
public class IdempotencyConflictException extends RuntimeException {
  /**
   * Constructs a new IdempotencyConflictException.
   *
   * @param message the detail message
   */
  public IdempotencyConflictException(String message) {
    super(message);
  }
}
