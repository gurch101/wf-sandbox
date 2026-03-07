package com.gurch.sandbox.idempotency;

/** Exception thrown when a mandatory Idempotency-Key header is missing. */
public class MissingIdempotencyKeyException extends RuntimeException {
  /**
   * Constructs a new MissingIdempotencyKeyException.
   *
   * @param message the detail message
   */
  public MissingIdempotencyKeyException(String message) {
    super(message);
  }
}
