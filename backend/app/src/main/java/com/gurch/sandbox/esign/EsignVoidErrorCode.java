package com.gurch.sandbox.esign;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation and business errors for void-envelope operations. */
public enum EsignVoidErrorCode implements ApiErrorCode {
  VOID_REASON_REQUIRED("reason", "reason is required", HttpStatus.BAD_REQUEST),
  ENVELOPE_ALREADY_VOIDED("id", "Envelope is already voided", HttpStatus.CONFLICT);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  EsignVoidErrorCode(String fieldName, String message, HttpStatus status) {
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
