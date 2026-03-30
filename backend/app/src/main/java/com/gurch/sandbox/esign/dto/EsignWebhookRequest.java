package com.gurch.sandbox.esign.dto;

import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Provider webhook payload used to update stored envelope and signer status.
 *
 * @param externalEnvelopeId provider envelope id
 * @param envelopeStatus updated envelope status
 * @param eventTimestamp timestamp emitted by the provider
 * @param certificateAvailable whether the provider certificate is ready for download
 * @param signers updated signer records
 */
@Schema(description = "Webhook payload carrying envelope and signer status updates")
public record EsignWebhookRequest(
    @Schema(description = "Provider envelope id", example = "stub-01JABCXYZ")
        String externalEnvelopeId,
    @Schema(description = "Updated envelope status") EsignEnvelopeStatus envelopeStatus,
    @Schema(description = "Timestamp emitted by the provider") Instant eventTimestamp,
    @Schema(description = "Whether the provider certificate is ready for download")
        boolean certificateAvailable,
    @Schema(description = "Updated signer records") List<SignerStatusUpdate> signers) {

  /**
   * Creates a webhook request with an immutable copy of signer updates.
   *
   * @param externalEnvelopeId provider envelope id
   * @param envelopeStatus updated envelope status
   * @param eventTimestamp timestamp emitted by the provider
   * @param certificateAvailable whether the provider certificate is ready for download
   * @param signers updated signer records
   */
  public EsignWebhookRequest {
    signers = signers == null ? List.of() : List.copyOf(signers);
  }

  /**
   * Nested signer status update.
   *
   * @param providerRecipientId provider recipient id used as the primary webhook correlation key
   * @param status updated signer status
   * @param viewedAt delivered/viewed timestamp reported by the provider
   * @param completedAt completed timestamp reported by the provider
   */
  public record SignerStatusUpdate(
      @Schema(description = "Provider recipient id used as the primary webhook correlation key")
          String providerRecipientId,
      @Schema(description = "Updated signer status") EsignSignerStatus status,
      @Schema(description = "Viewed timestamp") Instant viewedAt,
      @Schema(description = "Completed timestamp") Instant completedAt) {}
}
