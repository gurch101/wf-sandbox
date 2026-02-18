package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.requests.RequestPayloadHandler;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.requesttypes.PayloadHandlerCatalog;
import com.gurch.sandbox.web.ValidationErrorException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RequestPayloadHandlerRegistry implements PayloadHandlerCatalog {

  private final ObjectMapper objectMapper;
  private final Validator validator;
  private final Map<String, RequestPayloadHandler<?>> handlers;

  public RequestPayloadHandlerRegistry(
      ObjectMapper objectMapper, Validator validator, List<RequestPayloadHandler<?>> handlers) {
    this.objectMapper = objectMapper.copy();
    this.validator = validator;
    this.handlers =
        handlers.stream().collect(Collectors.toMap(RequestPayloadHandler::id, Function.identity()));
  }

  @Override
  public boolean exists(String handlerId) {
    return handlers.containsKey(handlerId);
  }

  public void validate(String handlerId, JsonNode payload) {
    RequestPayloadHandler<?> handler = handlers.get(handlerId);
    if (handler == null) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.MISSING_PAYLOAD_HANDLER);
    }
    validateTyped(handler, payload);
  }

  private <T> void validateTyped(RequestPayloadHandler<T> handler, JsonNode payload) {
    T typedPayload = toTypedPayload(payload, handler.payloadType());
    if (typedPayload == null) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
    }

    Set<ConstraintViolation<T>> violations = validator.validate(typedPayload);
    if (!violations.isEmpty()) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
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
}
