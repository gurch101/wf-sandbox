package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for request type delete operations. */
@Getter
@RequiredArgsConstructor
public enum RequestTypeDeleteValidationErrorCode implements ValidationErrorCode {
  REQUEST_TYPE_IN_USE("typeKey", "request type is used by at least one request");

  private final String fieldName;
  private final String message;
}
