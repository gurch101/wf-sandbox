package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Response representing current signer status for one envelope recipient. */
@Value
@Builder
@Schema(description = "Current signer state")
public class EsignSignerResponse {
  @Schema(description = "Anchor role key", example = "s1")
  String roleKey;

  @Schema(description = "Signature anchor text embedded in the PDF", example = "/s1/")
  String signatureAnchorText;

  @Schema(description = "Date-signed anchor text embedded in the PDF", example = "/d1/")
  String dateAnchorText;

  @Schema(description = "Routing order", example = "1")
  Integer routingOrder;

  @Schema(description = "Signer full name")
  String fullName;

  @Schema(description = "Signer email address")
  String email;

  @Schema(description = "Optional phone number")
  String phoneNumber;

  @Schema(description = "Signer authentication method")
  EsignAuthMethod authMethod;

  @Schema(description = "Provider recipient id")
  String providerRecipientId;

  @Schema(description = "Current signer status")
  EsignSignerStatus status;

  @Schema(description = "Timestamp when the signer viewed the envelope")
  Instant viewedAt;

  @Schema(description = "Timestamp when the signer completed signing")
  Instant completedAt;

  @Schema(description = "Timestamp of the most recent provider status update")
  Instant lastStatusAt;
}
