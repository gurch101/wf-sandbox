package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for request type management operations. */
public enum RequestTypeErrorCode implements ApiErrorCode {
  REQUEST_TYPE_NOT_FOUND("typeKey", "request type not found", HttpStatus.BAD_REQUEST),
  INVALID_PROCESS_DEFINITION_KEY(
      "processDefinitionKey", "process definition key is invalid", HttpStatus.BAD_REQUEST),
  INVALID_PAYLOAD_HANDLER_ID(
      "payloadHandlerId", "payload handler id is invalid", HttpStatus.BAD_REQUEST),
  INVALID_CONFIG_JSON("configJson", "config json is invalid", HttpStatus.BAD_REQUEST),
  REQUEST_TYPE_IN_USE(
      "typeKey", "request type is used by at least one request", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  RequestTypeErrorCode(String fieldName, String message, HttpStatus status) {
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
