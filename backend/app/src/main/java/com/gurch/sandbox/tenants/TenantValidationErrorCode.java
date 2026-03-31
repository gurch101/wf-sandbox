package com.gurch.sandbox.tenants;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for admin tenant CRUD operations. */
@Getter
@RequiredArgsConstructor
public enum TenantValidationErrorCode implements ValidationErrorCode {
  TENANT_NAME_ALREADY_EXISTS("name", "tenant name already exists"),
  TENANT_IN_USE("id", "tenant is referenced by at least one user");

  private final String fieldName;
  private final String message;
}
