package com.gurch.sandbox.documenttemplates.internal;

import com.gurch.sandbox.documenttemplates.DocumentTemplateSharedValidationErrorCode;
import com.gurch.sandbox.web.ValidationErrorException;

final class DocumentTemplateMimeTypes {

  static final String PDF = "application/pdf";
  static final String DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private DocumentTemplateMimeTypes() {}

  static boolean isSupported(String mimeType) {
    return PDF.equals(mimeType) || DOCX.equals(mimeType);
  }

  static void validateSupported(String mimeType) {
    if (!isSupported(mimeType)) {
      throw ValidationErrorException.of(
          DocumentTemplateSharedValidationErrorCode.UNSUPPORTED_FILE_TYPE);
    }
  }
}
