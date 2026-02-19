package com.gurch.sandbox.web;

import org.springframework.http.HttpStatus;

/** Contract for API error codes that can be surfaced in runtime responses and OpenAPI docs. */
public interface ApiErrorCode {
  /**
   * Returns the field name associated with the validation/business error.
   *
   * @return field name associated with the validation/business error
   */
  String fieldName();

  /** Returns stable machine-readable error code. */
  default String code() {
    if (this instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    throw new IllegalStateException(
        "ApiErrorCode implementations must override code() or be enums");
  }

  /**
   * Returns the human-readable default message.
   *
   * @return human-readable default message
   */
  String message();

  /**
   * Returns the HTTP status used when this error is returned.
   *
   * @return HTTP status used when this error is returned
   */
  HttpStatus status();
}
