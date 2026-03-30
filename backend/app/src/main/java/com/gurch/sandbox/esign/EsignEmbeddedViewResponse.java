package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Fresh embedded signing view URL for an in-person signer. */
@Value
@Builder
@Schema(description = "Ephemeral embedded signing view URL")
public class EsignEmbeddedViewResponse {
  @Schema(description = "Signer role key", example = "s1")
  String roleKey;

  @Schema(description = "Fresh DocuSign embedded signing URL")
  String signingUrl;
}
