package com.gurch.sandbox.esign;

import com.gurch.sandbox.web.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Validation and business errors for e-sign file-download operations. */
public enum EsignDownloadErrorCode implements ApiErrorCode {
  FILE_NOT_READY(
      "id", "Requested file is not available until the envelope is completed", HttpStatus.CONFLICT);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  EsignDownloadErrorCode(String fieldName, String message, HttpStatus status) {
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
