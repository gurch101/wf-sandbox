package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Envelope status payload received from DocuSign webhooks. */
@Value
@Builder
@Schema(description = "Envelope status update payload handled by e-sign webhook processing")
public class EsignWebhookStatusUpdate {
  String eventId;
  String envelopeId;
  String status;
  Instant statusChangedAt;
}
