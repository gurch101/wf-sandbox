package com.gurch.sandbox.requests;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors used by request submission flow. */
public enum RequestSubmissionErrorCode implements ApiErrorCode {
  INVALID_REQUEST_PAYLOAD("payload", "payload failed validation", HttpStatus.BAD_REQUEST),
  MISSING_PAYLOAD_HANDLER(
      "payloadHandlerId", "payload handler is not configured", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  RequestSubmissionErrorCode(String fieldName, String message, HttpStatus status) {
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
