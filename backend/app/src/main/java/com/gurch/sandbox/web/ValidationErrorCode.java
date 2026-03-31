package com.gurch.sandbox.web;

/** Contract for machine-readable validation errors surfaced in runtime responses and OpenAPI. */
public interface ValidationErrorCode {
  /** Returns the field name associated with the validation error. */
  String getFieldName();

  /** Returns stable machine-readable error code. */
  default String getCode() {
    if (this instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    throw new IllegalStateException(
        "ValidationErrorCode implementations must override getCode() or be enums");
  }

  /** Returns the human-readable default message. */
  String getMessage();
}
