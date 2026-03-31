package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.web.ValidationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Validation errors for document template update operations. */
@Getter
@RequiredArgsConstructor
public enum DocumentTemplateUpdateValidationErrorCode implements ValidationErrorCode {
  TEMPLATE_FIELD_MAP_CHANGED("file", "template field map changed and update is not allowed"),
  TEMPLATE_ESIGN_ANCHORS_CHANGED(
      "file", "template e-sign anchor metadata changed and update is not allowed");

  private final String fieldName;
  private final String message;
}
