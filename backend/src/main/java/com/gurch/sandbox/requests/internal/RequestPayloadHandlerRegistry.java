package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.requests.RequestPayloadHandler;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class RequestPayloadHandlerRegistry {

  private final ObjectMapper objectMapper;
  private final Map<String, RequestPayloadHandler<?>> handlers;

  public RequestPayloadHandlerRegistry(
      ObjectMapper objectMapper, List<RequestPayloadHandler<?>> handlers) {
    this.objectMapper = objectMapper.copy();
    this.handlers =
        handlers.stream()
            .collect(
                java.util.stream.Collectors.toMap(RequestPayloadHandler::id, Function.identity()));
  }

  public void validate(String handlerId, JsonNode payload) {
    RequestPayloadHandler<?> handler = handlers.get(handlerId);
    if (handler == null) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.MISSING_PAYLOAD_HANDLER);
    }
    validateTyped(handler, payload);
  }

  private <T> void validateTyped(RequestPayloadHandler<T> handler, JsonNode payload) {
    handler.validate(toTypedPayload(payload, handler.payloadType()));
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
