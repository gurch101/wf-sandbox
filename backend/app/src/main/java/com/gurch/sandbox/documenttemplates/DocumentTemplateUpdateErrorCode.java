package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for document template update operations. */
public enum DocumentTemplateUpdateErrorCode implements ApiErrorCode {
  TEMPLATE_FIELD_MAP_CHANGED(
      "file", "template field map changed and update is not allowed", HttpStatus.BAD_REQUEST),
  TEMPLATE_ESIGN_ANCHORS_CHANGED(
      "file",
      "template e-sign anchor metadata changed and update is not allowed",
      HttpStatus.BAD_REQUEST),
  INVALID_LANGUAGE("language", "language must be english or french", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  DocumentTemplateUpdateErrorCode(String fieldName, String message, HttpStatus status) {
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
