package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.dto.ValidationError;
import com.gurch.sandbox.requests.PreWorkflowPayloadValidator;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.requesttypes.PayloadHandlerCatalog;
import com.gurch.sandbox.web.ValidationErrorException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestPayloadHandlerRegistry implements PayloadHandlerCatalog {

  private final ObjectMapper objectMapper;
  private final Validator validator;
  private final Map<String, PreWorkflowPayloadValidator<?>> handlers;

  public RequestPayloadHandlerRegistry(
      ObjectMapper objectMapper,
      Validator validator,
      List<PreWorkflowPayloadValidator<?>> handlers) {
    this.objectMapper = objectMapper.copy();
    this.validator = validator;
    this.handlers =
        handlers.stream()
            .collect(Collectors.toMap(PreWorkflowPayloadValidator::id, Function.identity()));
  }

  @Override
  public boolean exists(String handlerId) {
    return handlers.containsKey(handlerId);
  }

  @Override
  public Class<?> payloadType(String handlerId) {
    PreWorkflowPayloadValidator<?> handler = handlers.get(handlerId);
    if (handler == null) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.MISSING_PAYLOAD_HANDLER);
    }
    return handler.payloadType();
  }

  public void validate(String handlerId, JsonNode payload) {
    PreWorkflowPayloadValidator<?> handler = handlers.get(handlerId);
    if (handler == null) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.MISSING_PAYLOAD_HANDLER);
    }
    validateTyped(handler, payload);
  }

  private <T> void validateTyped(PreWorkflowPayloadValidator<T> handler, JsonNode payload) {
    T typedPayload = toTypedPayload(payload, handler.payloadType());
    if (typedPayload == null) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
    }

    Set<ConstraintViolation<T>> violations = validator.validate(typedPayload);
    if (!violations.isEmpty()) {
      List<ValidationError> errors =
          violations.stream()
              .map(this::toValidationError)
              .sorted(
                  Comparator.comparing(ValidationError::name).thenComparing(ValidationError::code))
              .toList();
      throw ValidationErrorException.of(HttpStatus.BAD_REQUEST, errors);
    }

    handler.validate(typedPayload);
  }

  private <T> T toTypedPayload(JsonNode payload, Class<T> payloadType) {
    try {
      if (JsonNode.class.equals(payloadType)) {
        return payloadType.cast(payload);
      }
      return objectMapper.treeToValue(payload, payloadType);
    } catch (Exception e) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
    }
  }

  private ValidationError toValidationError(ConstraintViolation<?> violation) {
    String fieldName =
        violation.getPropertyPath() == null || violation.getPropertyPath().toString().isBlank()
            ? "payload"
            : violation.getPropertyPath().toString();
    String code =
        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    return new ValidationError(fieldName, code, violation.getMessage());
  }
}
