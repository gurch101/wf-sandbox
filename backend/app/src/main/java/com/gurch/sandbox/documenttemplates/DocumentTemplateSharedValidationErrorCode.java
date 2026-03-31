package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors shared across multiple document template operations. */
@Getter
@RequiredArgsConstructor
public enum DocumentTemplateSharedValidationErrorCode implements ValidationErrorCode {
  FILE_REQUIRED("file", "file is required"),
  FILE_READ_FAILED("file", "Could not read uploaded file content"),
  ORIGINAL_FILENAME_REQUIRED("file", "original filename is required"),
  UNSUPPORTED_FILE_TYPE("file", "Unsupported file type. Only PDF and DOCX are allowed");

  private final String fieldName;
  private final String message;
}
