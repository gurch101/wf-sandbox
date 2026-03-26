package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors shared across multiple document template operations. */
public enum DocumentTemplateSharedErrorCode implements ApiErrorCode {
  FILE_REQUIRED("file", "file is required", HttpStatus.BAD_REQUEST),
  FILE_READ_FAILED("file", "Could not read uploaded file content", HttpStatus.BAD_REQUEST),
  ORIGINAL_FILENAME_REQUIRED("file", "original filename is required", HttpStatus.BAD_REQUEST),
  UNSUPPORTED_FILE_TYPE(
      "file", "Unsupported file type. Only PDF and DOCX are allowed", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  DocumentTemplateSharedErrorCode(String fieldName, String message, HttpStatus status) {
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
