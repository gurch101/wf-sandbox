package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for document template upload operations. */
@Getter
@RequiredArgsConstructor
public enum DocumentTemplateUploadValidationErrorCode implements ValidationErrorCode {
  EN_NAME_REQUIRED("enName", "enName is required"),
  TENANT_SCOPE_MISMATCH("tenantId", "tenantId must match the authenticated user's tenant scope");

  private final String fieldName;
  private final String message;
}
