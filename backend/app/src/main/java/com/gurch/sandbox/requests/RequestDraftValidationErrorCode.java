package com.gurch.sandbox.requests;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for draft-only request operations. */
@Getter
@RequiredArgsConstructor
public enum RequestDraftValidationErrorCode implements ValidationErrorCode {
  INVALID_DRAFT_UPDATE_STATUS("status", "only DRAFT requests can be updated"),
  INVALID_DRAFT_SUBMIT_STATUS("status", "only DRAFT requests can be submitted");

  private final String fieldName;
  private final String message;
}
