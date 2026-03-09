package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Command payload for starting an embedded/in-person e-sign envelope. */
@Value
@Builder
public class StartEmbeddedEsignRequest {
  Long requestId;
  String documentName;
  byte[] documentPdf;
  String idempotencyKey;
  String emailSubject;
  String emailMessage;
  @Singular List<SignerRecipient> signers;
  @Singular List<CcRecipient> ccRecipients;

  /** Signer recipient details for embedded envelope creation. */
  @Value
  @Builder
  @Schema(description = "Embedded signer recipient details for envelope creation")
  public static class SignerRecipient {
    String recipientId;
    String name;
    String email;
    Integer routingOrder;
    String role;
  }

  /** Carbon-copy recipient details for embedded envelope creation. */
  @Value
  @Builder
  @Schema(description = "Embedded carbon-copy recipient details for envelope creation")
  public static class CcRecipient {
    String recipientId;
    String name;
    String email;
    Integer routingOrder;
  }
}
