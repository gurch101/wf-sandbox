package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.requests.AsyncPayloadValidationContext;
import com.gurch.sandbox.requests.AsyncPayloadValidationResult;
import com.gurch.sandbox.requests.AsyncPayloadValidator;
import org.springframework.stereotype.Component;

/** Default async validator used for request types without async business checks. */
@Component
public class NoopAsyncPayloadValidator implements AsyncPayloadValidator<JsonNode> {

  @Override
  public String id() {
    return "noop";
  }

  @Override
  public Class<JsonNode> payloadType() {
    return JsonNode.class;
  }

  @Override
  public AsyncPayloadValidationResult validate(
      JsonNode payload, AsyncPayloadValidationContext context) {
    return AsyncPayloadValidationResult.success();
  }
}
