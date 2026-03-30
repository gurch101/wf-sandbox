package com.gurch.sandbox.esign.dto;

import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Response representing current envelope and signer state. */
@Value
@Builder
@Schema(description = "Current envelope state including signer details")
public class EsignEnvelopeResponse {
  @Schema(description = "Internal envelope id", example = "42")
  Long id;

  @Schema(description = "Provider envelope id", example = "stub-01JABCXYZ")
  String externalEnvelopeId;

  @Schema(description = "Envelope subject")
  String subject;

  @Schema(description = "Envelope message")
  String message;

  @Schema(description = "Source file name")
  String fileName;

  @Schema(description = "Envelope delivery mode")
  EsignDeliveryMode deliveryMode;

  @Schema(description = "Current envelope status")
  EsignEnvelopeStatus status;

  @Schema(description = "Optional tenant id", example = "1")
  Integer tenantId;

  @Schema(description = "Whether reminders are enabled")
  boolean remindersEnabled;

  @Schema(description = "Configured reminder interval in hours", example = "24")
  Integer reminderIntervalHours;

  @Schema(description = "Reason provided when the envelope was voided")
  String voidedReason;

  @Schema(description = "Completion timestamp when fully signed")
  Instant completedAt;

  @Schema(description = "Last provider update timestamp")
  Instant lastProviderUpdateAt;

  @Schema(description = "Whether the signing certificate can be downloaded")
  boolean certificateReady;

  @Schema(description = "Whether the completed signed PDF can be downloaded")
  boolean signedDocumentReady;

  @Schema(description = "Signer states in routing order")
  List<EsignSignerResponse> signers;

  @Schema(description = "Created timestamp")
  Instant createdAt;

  @Schema(description = "Updated timestamp")
  Instant updatedAt;
}
