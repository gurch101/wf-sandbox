package com.gurch.sandbox.web.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.gurch.sandbox.idempotency.IdempotencyConflictException;
import com.gurch.sandbox.idempotency.MissingIdempotencyKeyException;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

  private final ExposedGlobalExceptionHandler handler = new ExposedGlobalExceptionHandler();

  @Test
  void shouldHandleSimpleExceptions() {
    assertThat(
            handler.handleIllegalArgumentException(new IllegalArgumentException("bad")).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST.value());

    assertThat(
            handler
                .handleIdempotencyConflictException(new IdempotencyConflictException("conflict"))
                .getStatus())
        .isEqualTo(HttpStatus.CONFLICT.value());

    assertThat(
            handler
                .handleMissingIdempotencyKeyException(new MissingIdempotencyKeyException("missing"))
                .getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST.value());

    assertThat(handler.handleNotFoundException(new NotFoundException("missing")).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND.value());

    assertThat(
            handler
                .handleOptimisticLockingFailureException(
                    new OptimisticLockingFailureException("stale"))
                .getStatus())
        .isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void shouldHandleValidationErrorException() {
    ServletWebRequest request =
        new ServletWebRequest(new MockHttpServletRequest("GET", "/api/test"));
    var problem =
        handler.handleValidationErrorException(
            ValidationErrorException.of(
                com.gurch.sandbox.requests.RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD),
            request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getTitle()).isEqualTo("Validation Failed");
    assertThat(problem.getProperties()).containsKey("errors");
  }

  @Test
  void shouldHandleMethodArgumentNotValid() throws Exception {
    MapBindingResult bindingResult = new MapBindingResult(new java.util.HashMap<>(), "req");
    bindingResult.rejectValue("field", "NotBlank", "must not be blank");

    Method method = DummyController.class.getDeclaredMethod("accept", String.class);
    MethodParameter methodParameter = new MethodParameter(method, 0);
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(methodParameter, bindingResult);

    ResponseEntity<Object> response =
        handler.callHandleMethodArgumentNotValid(
            ex,
            new HttpHeaders(),
            HttpStatus.BAD_REQUEST,
            new ServletWebRequest(new MockHttpServletRequest("POST", "/api/test")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isInstanceOf(org.springframework.http.ProblemDetail.class);
  }

  @Test
  void shouldHandleHttpMessageNotReadableBranches() {
    ServletWebRequest request =
        new ServletWebRequest(new MockHttpServletRequest("POST", "/api/test"));

    InvalidFormatException invalidEnum =
        InvalidFormatException.from(null, "bad enum", "BAD", RequestStatus.class);
    invalidEnum.prependPath(new Object(), "status");
    assertThat(callReadable(invalidEnum, request).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    JsonMappingException mapping =
        new JsonMappingException((com.fasterxml.jackson.core.JsonParser) null, "bad");
    mapping.prependPath(new Object(), "nested");
    assertThat(callReadable(mapping, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonParseException parse =
        new JsonParseException((com.fasterxml.jackson.core.JsonParser) null, "bad");
    assertThat(callReadable(parse, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(callReadable(new RuntimeException("boom"), request).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private ResponseEntity<Object> callReadable(Throwable cause, ServletWebRequest request) {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException(
            "bad body", cause, new MockHttpInputMessage(new byte[0]));
    return handler.callHandleHttpMessageNotReadable(
        ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
  }

  private static final class ExposedGlobalExceptionHandler extends GlobalExceptionHandler {
    ResponseEntity<Object> callHandleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatus status,
        ServletWebRequest request) {
      return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    ResponseEntity<Object> callHandleHttpMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpHeaders headers,
        HttpStatus status,
        ServletWebRequest request) {
      return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }
  }

  @SuppressWarnings("unused")
  private static final class DummyController {
    void accept(String value) {
      // no-op
    }
  }
}
