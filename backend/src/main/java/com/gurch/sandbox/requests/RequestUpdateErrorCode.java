package com.gurch.sandbox.requests;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for request update operations. */
public enum RequestUpdateErrorCode implements ApiErrorCode {
  INVALID_UPDATE_STATUS_TRANSITION(
      "status",
      "status transition must remain DRAFT or move DRAFT -> SUBMITTED for update requests",
      HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  RequestUpdateErrorCode(String fieldName, String message, HttpStatus status) {
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
