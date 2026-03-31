package com.gurch.sandbox.web;

import com.gurch.sandbox.dto.ValidationError;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** Exception carrying one or more validation errors in the API error response format. */
public final class ValidationErrorException extends RuntimeException {

  private final List<ValidationError> errors;

  private ValidationErrorException(List<ValidationError> errors, String message) {
    super(message);
    this.errors = List.copyOf(errors);
  }

  /**
   * Creates an exception from a list of error codes. All error codes must share the same status.
   *
   * @param errorCodes error codes to include in the response payload
   * @return exception containing mapped {@link ValidationError} values
   */
  public static ValidationErrorException from(List<? extends ValidationErrorCode> errorCodes) {
    validateInput(errorCodes);

    List<ValidationError> errors =
        errorCodes.stream()
            .map(
                code -> new ValidationError(code.getFieldName(), code.getCode(), code.getMessage()))
            .toList();
    return new ValidationErrorException(errors, buildMessage(errorCodes));
  }

  /**
   * Creates an exception from one or more error codes.
   *
   * @param errorCodes one or more error codes
   * @return exception containing mapped {@link ValidationError} values
   */
  public static ValidationErrorException of(ValidationErrorCode... errorCodes) {
    return from(Arrays.asList(errorCodes));
  }

  /**
   * Creates an exception from explicit validation errors.
   *
   * @param errors mapped validation errors
   * @return exception containing provided validation errors
   */
  public static ValidationErrorException of(List<ValidationError> errors) {
    if (errors == null || errors.isEmpty()) {
      throw new IllegalArgumentException("ValidationErrorException requires at least one error");
    }
    String message =
        errors.stream().map(ValidationError::message).collect(Collectors.joining("; "));
    return new ValidationErrorException(errors, message);
  }

  /**
   * Returns the HTTP status for the grouped validation errors.
   *
   * @return HTTP status for the grouped validation errors
   */
  public HttpStatus getStatus() {
    return HttpStatus.BAD_REQUEST;
  }

  /**
   * Returns the immutable list of API validation errors.
   *
   * @return immutable list of API validation errors
   */
  public List<ValidationError> getErrors() {
    return List.copyOf(errors);
  }

  private static void validateInput(List<? extends ValidationErrorCode> errorCodes) {
    if (errorCodes == null || errorCodes.isEmpty()) {
      throw new IllegalArgumentException("ValidationErrorException requires at least one error");
    }
  }

  private static String buildMessage(List<? extends ValidationErrorCode> errorCodes) {
    if (errorCodes == null || errorCodes.isEmpty()) {
      return "Validation failed";
    }

    return errorCodes.stream()
        .map(ValidationErrorCode::getMessage)
        .collect(Collectors.joining("; "));
  }
}
