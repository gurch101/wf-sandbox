package com.gurch.sandbox.esign;

import lombok.Builder;
import lombok.Value;

/** Command payload for voiding an envelope. */
@Value
@Builder
public class VoidEsignEnvelopeRequest {
  String envelopeId;
  String reason;
}
