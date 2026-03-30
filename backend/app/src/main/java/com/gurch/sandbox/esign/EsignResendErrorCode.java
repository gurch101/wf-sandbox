package com.gurch.sandbox.esign;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation and business errors for resend-email operations. */
public enum EsignResendErrorCode implements ApiErrorCode {
  RESEND_REMOTE_ONLY(
      "deliveryMode", "Resend is only available for remote envelopes", HttpStatus.CONFLICT),
  ENVELOPE_NOT_ACTIONABLE(
      "id", "Envelope cannot be resent in its current status", HttpStatus.CONFLICT),
  SIGNER_NOT_ACTIONABLE(
      "roleKey", "Signer cannot be resent in its current status", HttpStatus.CONFLICT),
  NO_ACTIONABLE_SIGNERS("id", "Envelope has no actionable signers to resend", HttpStatus.CONFLICT);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  EsignResendErrorCode(String fieldName, String message, HttpStatus status) {
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
