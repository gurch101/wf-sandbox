package com.gurch.sandbox.requests;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for draft-only operations. */
public enum RequestDraftErrorCode implements ApiErrorCode {
  INVALID_DRAFT_UPDATE_STATUS(
      "status", "only DRAFT requests can be updated", HttpStatus.BAD_REQUEST),
  INVALID_DRAFT_SUBMIT_STATUS(
      "status", "only DRAFT requests can be submitted", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  RequestDraftErrorCode(String fieldName, String message, HttpStatus status) {
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
