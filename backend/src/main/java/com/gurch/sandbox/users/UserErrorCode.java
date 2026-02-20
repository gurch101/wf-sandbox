package com.gurch.sandbox.users;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation errors for admin user CRUD operations. */
public enum UserErrorCode implements ApiErrorCode {
  USERNAME_ALREADY_EXISTS("username", "username already exists", HttpStatus.BAD_REQUEST),
  EMAIL_ALREADY_EXISTS("email", "email already exists", HttpStatus.BAD_REQUEST),
  TENANT_NOT_FOUND("tenantId", "tenant not found", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  UserErrorCode(String fieldName, String message, HttpStatus status) {
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
