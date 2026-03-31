package com.gurch.sandbox.web;

/** Exception thrown when a request conflicts with the current resource state. */
public class ConflictException extends RuntimeException {
  /**
   * Creates a conflict exception with a detailed message.
   *
   * @param message the detail message
   */
  public ConflictException(String message) {
    super(message);
  }
}
