package com.gurch.sandbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

  @Test
  void shouldRejectEmptyInputs() {
    IllegalArgumentException thrown = null;
    try {
      throw ValidationErrorException.from(List.of());
    } catch (IllegalArgumentException e) {
      thrown = e;
    }
    assertThat(thrown).hasMessageContaining("at least one error");
  }

  private enum TestErrorCode implements ValidationErrorCode {
    NAME_REQUIRED("name", "name is required"),
    STATUS_REQUIRED("status", "status is required");
    private final String fieldName;
    private final String message;

    TestErrorCode(String fieldName, String message) {
      this.fieldName = fieldName;
      this.message = message;
    }

    @Override
    public String getFieldName() {
      return fieldName;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }
}
