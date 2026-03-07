package com.gurch.sandbox.transfers.internal;

import com.gurch.sandbox.requests.AsyncPayloadValidationContext;
import com.gurch.sandbox.requests.AsyncPayloadValidationResult;
import com.gurch.sandbox.requests.AsyncPayloadValidator;
import org.springframework.stereotype.Component;

/** Async validator for money-in requests. */
@Component
public class MoneyInAsyncPayloadValidator
    implements AsyncPayloadValidator<MoneyInPayloadHandler.MoneyInPayload> {

  @Override
  public String id() {
    return "money-in";
  }

  @Override
  public Class<MoneyInPayloadHandler.MoneyInPayload> payloadType() {
    return MoneyInPayloadHandler.MoneyInPayload.class;
  }

  @Override
  public AsyncPayloadValidationResult validate(
      MoneyInPayloadHandler.MoneyInPayload payload, AsyncPayloadValidationContext context) {
    return AsyncPayloadValidationResult.success();
  }
}
