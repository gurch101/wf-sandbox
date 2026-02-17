package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.requests.RequestPayloadHandler;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.web.ValidationErrorException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Typed payload handler for loan-like requests.
 *
 * <p>The payload is deserialized to {@link AmountPositivePayload} and validated with Bean
 * Validation constraints.
 */
@Component
@RequiredArgsConstructor
public class AmountPositivePayloadHandler
    implements RequestPayloadHandler<AmountPositivePayloadHandler.AmountPositivePayload> {

  private final Validator validator;

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
    if (payload == null) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
    }
    Set<ConstraintViolation<AmountPositivePayload>> violations = validator.validate(payload);
    if (!violations.isEmpty()) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
    }
  }

  /** Structured payload for amount-positive request types. */
  public static final class AmountPositivePayload {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    private BigDecimal amount;

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }
  }
}
