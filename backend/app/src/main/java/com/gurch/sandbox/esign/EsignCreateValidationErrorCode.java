package com.gurch.sandbox.esign;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for create-envelope operations. */
@Getter
@RequiredArgsConstructor
public enum EsignCreateValidationErrorCode implements ValidationErrorCode {
  FILE_REQUIRED("file", "file is required"),
  FILE_READ_FAILED("file", "Could not read uploaded file content"),
  SUBJECT_REQUIRED("subject", "subject is required"),
  SIGNERS_REQUIRED("signers", "At least one signer is required"),
  INVALID_ANCHOR_KEY("signers.anchorKey", "anchorKey must match s<number>"),
  DUPLICATE_ANCHOR_KEY("signers.anchorKey", "Each signer anchorKey must be unique"),
  MISSING_SIGNATURE_ANCHOR("file", "Uploaded PDF is missing a required signature anchor"),
  REMOTE_EMAIL_REQUIRED("signers.email", "Remote signing requires a signer email address"),
  PASSCODE_REQUIRED("signers.passcode", "passcode is required for PASSCODE authentication"),
  SMS_NUMBER_REQUIRED(
      "signers.smsNumber", "smsNumber is required for SMS delivery or SMS authentication"),
  SMS_NUMBER_INVALID(
      "signers.smsNumber",
      "smsNumber must be a valid phone number; use +E.164 or a common US format"),
  SMS_DELIVERY_SMS_AUTH_NOT_SUPPORTED(
      "signers", "SMS delivery cannot be combined with SMS authentication"),
  IN_PERSON_AUTH_NOT_ALLOWED(
      "signers.authMethod", "In-person signing must use NONE authentication"),
  REMINDER_INTERVAL_REQUIRED(
      "reminderIntervalHours", "reminderIntervalHours is required when reminders are enabled"),
  REMINDERS_REMOTE_ONLY("deliveryMode", "Reminders are only supported for remote signing");

  private final String fieldName;
  private final String message;
}
