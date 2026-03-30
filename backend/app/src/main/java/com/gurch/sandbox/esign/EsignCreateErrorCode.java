package com.gurch.sandbox.esign;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation and business errors for create-envelope operations. */
public enum EsignCreateErrorCode implements ApiErrorCode {
  FILE_REQUIRED("file", "file is required", HttpStatus.BAD_REQUEST),
  FILE_READ_FAILED("file", "Could not read uploaded file content", HttpStatus.BAD_REQUEST),
  SUBJECT_REQUIRED("subject", "subject is required", HttpStatus.BAD_REQUEST),
  SIGNERS_REQUIRED("signers", "At least one signer is required", HttpStatus.BAD_REQUEST),
  INVALID_ANCHOR_KEY("signers.anchorKey", "anchorKey must match s<number>", HttpStatus.BAD_REQUEST),
  DUPLICATE_ANCHOR_KEY(
      "signers.anchorKey", "Each signer anchorKey must be unique", HttpStatus.BAD_REQUEST),
  MISSING_SIGNATURE_ANCHOR(
      "file", "Uploaded PDF is missing a required signature anchor", HttpStatus.BAD_REQUEST),
  REMOTE_EMAIL_REQUIRED(
      "signers.email", "Remote signing requires a signer email address", HttpStatus.BAD_REQUEST),
  PASSCODE_REQUIRED(
      "signers.passcode",
      "passcode is required for PASSCODE authentication",
      HttpStatus.BAD_REQUEST),
  SMS_NUMBER_REQUIRED(
      "signers.smsNumber",
      "smsNumber is required for SMS delivery or SMS authentication",
      HttpStatus.BAD_REQUEST),
  SMS_NUMBER_INVALID(
      "signers.smsNumber",
      "smsNumber must be a valid phone number; use +E.164 or a common US format",
      HttpStatus.BAD_REQUEST),
  SMS_DELIVERY_SMS_AUTH_NOT_SUPPORTED(
      "signers", "SMS delivery cannot be combined with SMS authentication", HttpStatus.BAD_REQUEST),
  IN_PERSON_AUTH_NOT_ALLOWED(
      "signers.authMethod",
      "In-person signing must use NONE authentication",
      HttpStatus.BAD_REQUEST),
  REMINDER_INTERVAL_REQUIRED(
      "reminderIntervalHours",
      "reminderIntervalHours is required when reminders are enabled",
      HttpStatus.BAD_REQUEST),
  REMINDERS_REMOTE_ONLY(
      "deliveryMode", "Reminders are only supported for remote signing", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  EsignCreateErrorCode(String fieldName, String message, HttpStatus status) {
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
