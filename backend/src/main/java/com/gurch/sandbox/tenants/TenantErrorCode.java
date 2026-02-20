package com.gurch.sandbox.tenants;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for admin tenant CRUD operations. */
public enum TenantErrorCode implements ApiErrorCode {
  TENANT_NAME_ALREADY_EXISTS("name", "tenant name already exists", HttpStatus.BAD_REQUEST),
  TENANT_IN_USE("id", "tenant is referenced by at least one user", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  TenantErrorCode(String fieldName, String message, HttpStatus status) {
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
