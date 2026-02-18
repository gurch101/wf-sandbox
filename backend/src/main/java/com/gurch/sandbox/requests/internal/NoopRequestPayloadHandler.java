package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.requests.PreWorkflowPayloadValidator;
import org.springframework.stereotype.Component;

@Component
public class NoopRequestPayloadHandler implements PreWorkflowPayloadValidator<JsonNode> {

  @Override
  public String id() {
    return "noop";
  }

  @Override
  public Class<JsonNode> payloadType() {
    return JsonNode.class;
  }

  @Override
  public void validate(JsonNode payload) {
    // No-op handler used for payloads without synchronous business rules.
  }
}
