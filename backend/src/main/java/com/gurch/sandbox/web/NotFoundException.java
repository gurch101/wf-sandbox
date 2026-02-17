package com.gurch.sandbox.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when a requested resource is not found. Maps to HTTP 404 Not Found. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
  /**
   * Constructs a new NotFoundException with the specified message.
   *
   * @param message the detail message
   */
  public NotFoundException(String message) {
    super(message);
  }
}
