package com.gurch.sandbox.esign;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation and business errors for e-sign endpoints. */
public enum EsignErrorCode implements ApiErrorCode {
  FILE_REQUIRED("file", "file is required", HttpStatus.BAD_REQUEST),
  FILE_READ_FAILED("file", "Could not read uploaded file content", HttpStatus.BAD_REQUEST),
  UNSUPPORTED_FILE_TYPE("file", "Only PDF uploads are supported", HttpStatus.BAD_REQUEST),
  SUBJECT_REQUIRED("subject", "subject is required", HttpStatus.BAD_REQUEST),
  SIGNERS_REQUIRED("signers", "At least one signer is required", HttpStatus.BAD_REQUEST),
  ANCHOR_KEY_REQUIRED("signers.anchorKey", "anchorKey is required", HttpStatus.BAD_REQUEST),
  DUPLICATE_ANCHOR_KEY(
      "signers.anchorKey", "Each signer anchorKey must be unique", HttpStatus.BAD_REQUEST),
  INVALID_ANCHOR_KEY(
      "signers.anchorKey", "anchorKey must match s<number>", HttpStatus.BAD_REQUEST),
  MISSING_SIGNATURE_ANCHOR(
      "file", "Uploaded PDF is missing a required signature anchor", HttpStatus.BAD_REQUEST),
  REMOTE_AUTH_REQUIRED(
      "signers.authMethod",
      "Remote signing requires PASSCODE or SMS authentication",
      HttpStatus.BAD_REQUEST),
  PASSCODE_REQUIRED(
      "signers.passcode", "passcode is required for PASSCODE authentication", HttpStatus.BAD_REQUEST),
  SMS_NUMBER_REQUIRED(
      "signers.smsNumber", "smsNumber is required for SMS authentication", HttpStatus.BAD_REQUEST),
  IN_PERSON_AUTH_NOT_ALLOWED(
      "signers.authMethod", "In-person signing must use NONE authentication", HttpStatus.BAD_REQUEST),
  EMBEDDED_VIEW_IN_PERSON_ONLY(
      "deliveryMode", "Embedded signing views are only available for in-person envelopes", HttpStatus.CONFLICT),
  REMINDER_INTERVAL_REQUIRED(
      "reminderIntervalHours",
      "reminderIntervalHours is required when reminders are enabled",
      HttpStatus.BAD_REQUEST),
  REMINDERS_REMOTE_ONLY(
      "deliveryMode", "Reminders are only supported for remote signing", HttpStatus.BAD_REQUEST),
  VOID_REASON_REQUIRED("reason", "reason is required", HttpStatus.BAD_REQUEST),
  CERTIFICATE_NOT_READY(
      "id", "Signing certificate is not available until the envelope is completed", HttpStatus.CONFLICT),
  SIGNED_DOCUMENT_NOT_READY(
      "id", "Signed document is not available until the envelope is completed", HttpStatus.CONFLICT),
  REMINDER_NOT_ALLOWED(
      "id", "Reminders can only be sent for active remote envelopes", HttpStatus.CONFLICT),
  ENVELOPE_ALREADY_VOIDED("id", "Envelope is already voided", HttpStatus.CONFLICT);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  EsignErrorCode(String fieldName, String message, HttpStatus status) {
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
