package com.gurch.sandbox.requests;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for request create operations. */
public enum RequestCreateErrorCode implements ApiErrorCode {
  INVALID_CREATE_STATUS(
      "status",
      "status must be one of [DRAFT, SUBMITTED] for create requests",
      HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  RequestCreateErrorCode(String fieldName, String message, HttpStatus status) {
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
