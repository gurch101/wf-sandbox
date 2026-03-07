package com.gurch.sandbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ValidationErrorExceptionTest {

  @Test
  void shouldCaptureMultipleValidationErrorsInSingleException() {
    ValidationErrorException exception =
        ValidationErrorException.of(TestErrorCode.NAME_REQUIRED, TestErrorCode.STATUS_REQUIRED);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getErrors()).hasSize(2);
    assertThat(exception.getErrors().get(0).name()).isEqualTo("name");
    assertThat(exception.getErrors().get(0).code()).isEqualTo("NAME_REQUIRED");
    assertThat(exception.getErrors().get(1).name()).isEqualTo("status");
    assertThat(exception.getErrors().get(1).code()).isEqualTo("STATUS_REQUIRED");
  }

  private enum TestErrorCode implements ApiErrorCode {
    NAME_REQUIRED("name", "name is required", HttpStatus.BAD_REQUEST),
    STATUS_REQUIRED("status", "status is required", HttpStatus.BAD_REQUEST);
    private final String fieldName;
    private final String message;
    private final HttpStatus status;

    TestErrorCode(String fieldName, String message, HttpStatus status) {
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
}
