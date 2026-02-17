package com.gurch.sandbox.web;

/** Exception thrown when uploaded content exceeds configured maximum size. */
public class PayloadTooLargeException extends RuntimeException {

  /** Creates a payload-too-large exception with a detailed message. */
  public PayloadTooLargeException(String message) {
    super(message);
  }
}
