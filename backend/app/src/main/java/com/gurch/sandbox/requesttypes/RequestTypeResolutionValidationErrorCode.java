package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Request-type lookup errors used by request create/submit flows. */
@Getter
@RequiredArgsConstructor
public enum RequestTypeResolutionValidationErrorCode implements ValidationErrorCode {
  REQUEST_TYPE_NOT_FOUND("typeKey", "request type not found");

  private final String fieldName;
  private final String message;
}
