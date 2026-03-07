package com.gurch.sandbox.web;

import com.gurch.sandbox.dto.ValidationError;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** Exception carrying one or more validation errors in the API error response format. */
public final class ValidationErrorException extends RuntimeException {

  private final HttpStatus status;
  private final List<ValidationError> errors;

  private ValidationErrorException(
      HttpStatus status, List<ValidationError> errors, String message) {
    super(message);
    this.status = status;
    this.errors = List.copyOf(errors);
  }

  /**
   * Creates an exception from a list of error codes. All error codes must share the same status.
   *
   * @param errorCodes error codes to include in the response payload
   * @return exception containing mapped {@link ValidationError} values
   */
  public static ValidationErrorException from(List<? extends ApiErrorCode> errorCodes) {
    validateInput(errorCodes);
    Set<HttpStatus> statuses =
        errorCodes.stream().map(ApiErrorCode::status).collect(Collectors.toSet());
    if (statuses.size() != 1) {
      throw new IllegalArgumentException(
          "All validation errors in one exception must share the same HTTP status");
    }

    HttpStatus status = statuses.iterator().next();
    List<ValidationError> errors =
        errorCodes.stream()
            .map(code -> new ValidationError(code.fieldName(), code.code(), code.message()))
            .toList();
    return new ValidationErrorException(status, errors, buildMessage(errorCodes));
  }

  /**
   * Creates an exception from one or more error codes.
   *
   * @param errorCodes one or more error codes
   * @return exception containing mapped {@link ValidationError} values
   */
  public static ValidationErrorException of(ApiErrorCode... errorCodes) {
    return from(Arrays.asList(errorCodes));
  }

  /**
   * Creates an exception from explicit validation errors using a single HTTP status.
   *
   * @param status status to return
   * @param errors mapped validation errors
   * @return exception containing provided validation errors
   */
  public static ValidationErrorException of(HttpStatus status, List<ValidationError> errors) {
    if (status == null) {
      throw new IllegalArgumentException("ValidationErrorException requires a non-null status");
    }
    if (errors == null || errors.isEmpty()) {
      throw new IllegalArgumentException("ValidationErrorException requires at least one error");
    }
    String message =
        errors.stream().map(ValidationError::message).collect(Collectors.joining("; "));
    return new ValidationErrorException(status, errors, message);
  }

  /**
   * Returns the HTTP status for the grouped validation errors.
   *
   * @return HTTP status for the grouped validation errors
   */
  public HttpStatus getStatus() {
    return status;
  }

  /**
   * Returns the immutable list of API validation errors.
   *
   * @return immutable list of API validation errors
   */
  public List<ValidationError> getErrors() {
    return List.copyOf(errors);
  }

  private static void validateInput(List<? extends ApiErrorCode> errorCodes) {
    if (errorCodes == null || errorCodes.isEmpty()) {
      throw new IllegalArgumentException("ValidationErrorException requires at least one error");
    }
  }

  private static String buildMessage(List<? extends ApiErrorCode> errorCodes) {
    if (errorCodes == null || errorCodes.isEmpty()) {
      return "Validation failed";
    }

    return errorCodes.stream().map(ApiErrorCode::message).collect(Collectors.joining("; "));
  }
}
