package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.requests.RequestPayloadHandler;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.web.ValidationErrorException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Typed payload handler for loan-like requests.
 *
 * <p>The payload is deserialized to {@link AmountPositivePayload} and validated with Bean
 * Validation constraints.
 */
@Component
public class AmountPositivePayloadHandler
    implements RequestPayloadHandler<AmountPositivePayloadHandler.AmountPositivePayload> {

  @Override
  public String id() {
    return "amount-positive";
  }

  @Override
  public Class<AmountPositivePayload> payloadType() {
    return AmountPositivePayload.class;
  }

  @Override
  public void validate(AmountPositivePayload payload) {
    if (payload.getAmount().signum() <= 0) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
    }
  }

  /** Structured payload for amount-positive request types. */
  public static final class AmountPositivePayload {
    @NotNull @Positive private BigDecimal amount;

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }
  }
}
