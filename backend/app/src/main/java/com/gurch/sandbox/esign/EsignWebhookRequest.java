package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Provider webhook payload used to update stored envelope and signer status. */
@Schema(description = "Webhook payload carrying envelope and signer status updates")
public record EsignWebhookRequest(
    @Schema(description = "Provider envelope id", example = "stub-01JABCXYZ")
        String externalEnvelopeId,
    @Schema(description = "Updated envelope status")
        EsignEnvelopeStatus envelopeStatus,
    @Schema(description = "Timestamp emitted by the provider")
        Instant eventTimestamp,
    @Schema(description = "Whether the provider certificate is ready for download")
        boolean certificateAvailable,
    @Schema(description = "Updated signer records")
        List<SignerStatusUpdate> signers) {

  /** Nested signer status update. */
  public record SignerStatusUpdate(
      @Schema(description = "Anchor role key", example = "s1")
          String roleKey,
      @Schema(description = "Provider recipient id")
          String providerRecipientId,
      @Schema(description = "Updated signer status")
          EsignSignerStatus status,
      @Schema(description = "Viewed timestamp")
          Instant viewedAt,
      @Schema(description = "Completed timestamp")
          Instant completedAt) {}
}
