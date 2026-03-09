package com.gurch.sandbox.esign;

import lombok.Builder;
import lombok.Value;

/** Command payload for resending an existing envelope. */
@Value
@Builder
public class ResendEsignEnvelopeRequest {
  String envelopeId;
}
