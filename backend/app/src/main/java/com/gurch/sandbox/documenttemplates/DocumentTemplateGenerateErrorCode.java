package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for document template generation operations. */
public enum DocumentTemplateGenerateErrorCode implements ApiErrorCode {
  GENERATE_DOCUMENTS_REQUIRED(
      "documents", "At least one document is required", HttpStatus.BAD_REQUEST),
  GENERATE_TEMPLATE_ID_REQUIRED(
      "documents.documentTemplateId",
      "Each document must include documentTemplateId",
      HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  DocumentTemplateGenerateErrorCode(String fieldName, String message, HttpStatus status) {
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
