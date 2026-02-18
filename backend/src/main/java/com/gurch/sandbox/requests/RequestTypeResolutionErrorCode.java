package com.gurch.sandbox.requests;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Request-type lookup errors exposed by request create/submit APIs. */
public enum RequestTypeResolutionErrorCode implements ApiErrorCode {
  REQUEST_TYPE_NOT_FOUND("typeKey", "request type not found", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  RequestTypeResolutionErrorCode(String fieldName, String message, HttpStatus status) {
    this.fieldName = fieldName;
    this.message = message;
    this.status = status;
  }

  @Override
  public String fieldName() {
    return fieldName;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
