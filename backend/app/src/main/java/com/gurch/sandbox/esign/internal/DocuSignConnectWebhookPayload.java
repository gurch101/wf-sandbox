package com.gurch.sandbox.esign.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Raw DocuSign Connect 2.0 JSON SIM webhook payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocuSignConnectWebhookPayload(String event, Instant generatedDateTime, Data data) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Data(String envelopeId, String recipientId, EnvelopeSummary envelopeSummary) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EnvelopeSummary(String status) {}
}
