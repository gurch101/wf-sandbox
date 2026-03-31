package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for request type create/change commands. */
@Getter
@RequiredArgsConstructor
public enum RequestTypeCommandValidationErrorCode implements ValidationErrorCode {
  INVALID_PROCESS_DEFINITION_KEY("processDefinitionKey", "process definition key is invalid");

  private final String fieldName;
  private final String message;
}
