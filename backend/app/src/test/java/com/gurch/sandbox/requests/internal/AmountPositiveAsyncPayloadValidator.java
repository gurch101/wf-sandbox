package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.requests.AsyncPayloadValidationContext;
import com.gurch.sandbox.requests.AsyncPayloadValidationResult;
import com.gurch.sandbox.requests.AsyncPayloadValidator;
import org.springframework.stereotype.Component;

/** Async validator for amount-positive request types used in tests. */
@Component
public class AmountPositiveAsyncPayloadValidator
    implements AsyncPayloadValidator<AmountPositivePayloadHandler.AmountPositivePayload> {

  @Override
  public String id() {
    return "amount-positive";
  }

  @Override
  public Class<AmountPositivePayloadHandler.AmountPositivePayload> payloadType() {
    return AmountPositivePayloadHandler.AmountPositivePayload.class;
  }

  @Override
  public AsyncPayloadValidationResult validate(
      AmountPositivePayloadHandler.AmountPositivePayload payload,
      AsyncPayloadValidationContext context) {
    return AsyncPayloadValidationResult.success();
  }
}
