package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignRemoteDeliveryMethod;
import com.gurch.sandbox.esign.EsignSignatureMode;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class DocusignEnvelopeRequest {
  Long requestId;
  EsignSignatureMode signatureMode;
  String documentName;
  byte[] documentPdf;
  String idempotencyKey;
  String emailSubject;
  String emailMessage;
  @Singular List<SignerRecipient> signers;
  @Singular List<CcRecipient> ccRecipients;

  @Value
  @Builder
  public static class SignerRecipient {
    String recipientId;
    String name;
    String email;
    Integer routingOrder;
    String role;
    String signAnchor;
    String dateAnchor;
    EsignRemoteDeliveryMethod remoteDeliveryMethod;
    String smsCountryCode;
    String smsNumber;
    String accessCode;
  }

  @Value
  @Builder
  public static class CcRecipient {
    String recipientId;
    String name;
    String email;
    Integer routingOrder;
  }
}
