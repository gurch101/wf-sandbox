package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.requests.AsyncPayloadValidationContext;
import com.gurch.sandbox.requests.AsyncPayloadValidationResult;
import com.gurch.sandbox.requests.AsyncPayloadValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Registry that resolves and executes async payload validators by id. */
@Component
public class AsyncPayloadValidatorRegistry {

  private final ObjectMapper objectMapper;
  private final Validator validator;
  private final Map<String, AsyncPayloadValidator<?>> validators;

  public AsyncPayloadValidatorRegistry(
      ObjectMapper objectMapper, Validator validator, List<AsyncPayloadValidator<?>> validators) {
    this.objectMapper = objectMapper.copy();
    this.validator = validator;
    this.validators =
        validators.stream()
            .collect(Collectors.toMap(AsyncPayloadValidator::id, Function.identity()));
  }

  public AsyncPayloadValidationResult validate(
      String validatorId, JsonNode payload, AsyncPayloadValidationContext context) {
    AsyncPayloadValidator<?> validator = validators.get(validatorId);
    if (validator == null) {
      return AsyncPayloadValidationResult.failed(
          "No async validator configured for id '%s'".formatted(validatorId));
    }
    return validateTyped(validator, payload, context);
  }

  private <T> AsyncPayloadValidationResult validateTyped(
      AsyncPayloadValidator<T> validator, JsonNode payload, AsyncPayloadValidationContext context) {
    T typedPayload = toTypedPayload(payload, validator.payloadType());
    if (typedPayload == null) {
      return AsyncPayloadValidationResult.failed("payload could not be parsed");
    }

    Set<ConstraintViolation<T>> violations = this.validator.validate(typedPayload);
    if (!violations.isEmpty()) {
      String reason =
          violations.stream()
              .map(
                  violation ->
                      "%s %s".formatted(violation.getPropertyPath(), violation.getMessage()).trim())
              .collect(Collectors.joining("; "));
      return AsyncPayloadValidationResult.failed(reason);
    }

    return validator.validate(typedPayload, context);
  }

  private <T> T toTypedPayload(JsonNode payload, Class<T> payloadType) {
    try {
      if (JsonNode.class.equals(payloadType)) {
        return payloadType.cast(payload);
      }
      return objectMapper.treeToValue(payload, payloadType);
    } catch (Exception e) {
      return null;
    }
  }
}
