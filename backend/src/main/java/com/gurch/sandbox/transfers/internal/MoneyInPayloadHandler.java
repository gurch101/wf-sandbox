package com.gurch.sandbox.transfers.internal;

import com.gurch.sandbox.requests.RequestPayloadHandler;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.web.ValidationErrorException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Typed payload handler for money-in transfer requests. */
@Component
public class MoneyInPayloadHandler
    implements RequestPayloadHandler<MoneyInPayloadHandler.MoneyInPayload> {

  @Override
  public String id() {
    return "money-in";
  }

  @Override
  public Class<MoneyInPayload> payloadType() {
    return MoneyInPayload.class;
  }

  @Override
  public void validate(MoneyInPayload payload) {
    if (payload.getFromAccount().equals(payload.getToAccount())) {
      throw ValidationErrorException.of(RequestSubmissionErrorCode.INVALID_REQUEST_PAYLOAD);
    }
  }

  /** Structured payload for money-in transfer requests. */
  public static final class MoneyInPayload {

    @NotBlank private String fromAccount;
    @NotBlank private String toAccount;
    @NotNull @Positive private BigDecimal amount;

    public String getFromAccount() {
      return fromAccount;
    }

    public void setFromAccount(String fromAccount) {
      this.fromAccount = fromAccount;
    }

    public String getToAccount() {
      return toAccount;
    }

    public void setToAccount(String toAccount) {
      this.toAccount = toAccount;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }
  }
}
