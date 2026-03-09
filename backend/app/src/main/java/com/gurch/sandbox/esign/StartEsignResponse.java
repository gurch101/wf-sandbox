package com.gurch.sandbox.esign;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Result projection returned after envelope start is accepted. */
@Value
@Builder
public class StartEsignResponse {
  Long requestId;
  String envelopeId;
  EsignSignatureMode signatureMode;
  EsignEnvelopeStatus status;
  String recipientViewUrl;
  Instant createdAt;
}
