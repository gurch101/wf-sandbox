package com.gurch.sandbox.esign;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation and business errors for embedded signing-view operations. */
public enum EsignEmbeddedViewErrorCode implements ApiErrorCode {
  EMBEDDED_VIEW_IN_PERSON_ONLY(
      "deliveryMode",
      "Embedded signing views are only available for in-person envelopes",
      HttpStatus.CONFLICT);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  EsignEmbeddedViewErrorCode(String fieldName, String message, HttpStatus status) {
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
