package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for document template generation operations. */
@Getter
@RequiredArgsConstructor
public enum DocumentTemplateGenerateValidationErrorCode implements ValidationErrorCode {
  GENERATE_DOCUMENTS_REQUIRED("documents", "At least one document is required"),
  GENERATE_TEMPLATE_ID_REQUIRED(
      "documents.documentTemplateId", "Each document must include documentTemplateId");

  private final String fieldName;
  private final String message;
}
