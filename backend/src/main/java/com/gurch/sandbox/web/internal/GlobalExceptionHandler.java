package com.gurch.sandbox.web.internal;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.gurch.sandbox.dto.ValidationError;
import com.gurch.sandbox.idempotency.IdempotencyConflictException;
import com.gurch.sandbox.idempotency.MissingIdempotencyKeyException;
import com.gurch.sandbox.web.NotFoundException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgumentException(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ProblemDetail handleIdempotencyConflictException(IdempotencyConflictException e) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problemDetail.setTitle("Idempotency Conflict");
    problemDetail.setType(URI.create("https://example.com/probs/idempotency-conflict"));
    return problemDetail;
  }

  @ExceptionHandler(MissingIdempotencyKeyException.class)
  public ProblemDetail handleMissingIdempotencyKeyException(MissingIdempotencyKeyException e) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problemDetail.setTitle("Missing Idempotency Key");
    problemDetail.setType(URI.create("https://example.com/probs/missing-idempotency-key"));
    return problemDetail;
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new ValidationError(
                        error.getField(), error.getCode(), error.getDefaultMessage()))
            .toList();

    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(status, "Request has invalid fields");
    problemDetail.setTitle("Validation Failed");
    problemDetail.setType(URI.create("https://example.com/probs/validation-failed"));
    problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
    problemDetail.setProperty("errors", errors);

    return createResponseEntity(problemDetail, headers, status, request);
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Throwable cause = ex.getMostSpecificCause();
    List<ValidationError> errors;

    if (cause instanceof InvalidFormatException invalidFormatException) {
      String fieldName = toFieldName(invalidFormatException);
      errors =
          List.of(
              new ValidationError(
                  fieldName,
                  "INVALID_VALUE",
                  invalidValueMessage(fieldName, invalidFormatException)));
    } else if (cause instanceof JsonMappingException jsonMappingException) {
      String fieldName = toFieldName(jsonMappingException);
      errors =
          List.of(
              new ValidationError(
                  fieldName, "INVALID_VALUE", "%s contains an invalid value".formatted(fieldName)));
    } else if (cause instanceof JsonParseException) {
      errors =
          List.of(
              new ValidationError(
                  "body", "INVALID_JSON", "Request body contains invalid JSON syntax"));
    } else {
      errors =
          List.of(
              new ValidationError(
                  "body", "INVALID_REQUEST_BODY", "Request body contains invalid content"));
    }

    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(status, "Request body has invalid content");
    problemDetail.setTitle("Validation Failed");
    problemDetail.setType(URI.create("https://example.com/probs/validation-failed"));
    problemDetail.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
    problemDetail.setProperty("errors", errors);

    return createResponseEntity(problemDetail, headers, status, request);
  }

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFoundException(NotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLockingFailureException(
      OptimisticLockingFailureException e) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "The resource has been updated by another process. Please refresh and try again.");
    problemDetail.setTitle("Optimistic Locking Failure");
    return problemDetail;
  }

  private static String toFieldName(JsonMappingException exception) {
    String fieldPath =
        exception.getPath().stream()
            .map(
                reference ->
                    reference.getFieldName() != null
                        ? reference.getFieldName()
                        : "[" + reference.getIndex() + "]")
            .collect(Collectors.joining("."));
    return fieldPath.isBlank() ? "body" : fieldPath;
  }

  private static String invalidValueMessage(
      String fieldName, InvalidFormatException invalidFormatException) {
    Class<?> targetType = invalidFormatException.getTargetType();
    if (targetType != null && targetType.isEnum()) {
      Object[] allowedValues = targetType.getEnumConstants();
      String allowed =
          java.util.Arrays.stream(allowedValues)
              .map(String::valueOf)
              .collect(Collectors.joining(", "));
      return "%s contains an invalid value. Allowed values: %s".formatted(fieldName, allowed);
    }
    return "%s contains an invalid value".formatted(fieldName);
  }
}
