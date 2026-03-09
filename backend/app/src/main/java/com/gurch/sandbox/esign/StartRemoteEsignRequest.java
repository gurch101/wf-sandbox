package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/** Command payload for starting a remote e-sign envelope. */
@Value
@Builder
public class StartRemoteEsignRequest {
  Long requestId;
  String documentName;
  byte[] documentPdf;
  String idempotencyKey;
  String emailSubject;
  String emailMessage;
  @Singular List<SignerRecipient> signers;
  @Singular List<CcRecipient> ccRecipients;

  /** Signer recipient details for remote envelope creation. */
  @Value
  @Builder
  @Schema(description = "Remote signer recipient details for envelope creation")
  public static class SignerRecipient {
    String recipientId;
    String name;
    String email;
    Integer routingOrder;
    String role;
    EsignRemoteDeliveryMethod remoteDeliveryMethod;
    String smsCountryCode;
    String smsNumber;
    String accessCode;
  }

  /** Carbon-copy recipient details for remote envelope creation. */
  @Value
  @Builder
  @Schema(description = "Remote carbon-copy recipient details for envelope creation")
  public static class CcRecipient {
    String recipientId;
    String name;
    String email;
    Integer routingOrder;
  }
}
