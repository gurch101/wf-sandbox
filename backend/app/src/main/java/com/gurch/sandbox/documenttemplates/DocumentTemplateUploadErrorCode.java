package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for document template upload operations. */
public enum DocumentTemplateUploadErrorCode implements ApiErrorCode {
  EN_NAME_REQUIRED("enName", "enName is required", HttpStatus.BAD_REQUEST),
  TENANT_SCOPE_MISMATCH(
      "tenantId",
      "tenantId must match the authenticated user's tenant scope",
      HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  DocumentTemplateUploadErrorCode(String fieldName, String message, HttpStatus status) {
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
