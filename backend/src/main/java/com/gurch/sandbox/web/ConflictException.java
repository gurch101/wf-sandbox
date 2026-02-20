package com.gurch.sandbox.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when a request conflicts with current server state. */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
  /** Creates a conflict exception with the provided detail message. */
  public ConflictException(String message) {
    super(message);
  }
}
