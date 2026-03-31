package com.gurch.sandbox.users;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for admin user CRUD operations. */
@Getter
@RequiredArgsConstructor
public enum UserValidationErrorCode implements ValidationErrorCode {
  USERNAME_ALREADY_EXISTS("username", "username already exists"),
  EMAIL_ALREADY_EXISTS("email", "email already exists"),
  TENANT_NOT_FOUND("tenantId", "tenant not found");

  private final String fieldName;
  private final String message;
}
