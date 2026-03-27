package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Parsed e-sign anchor metadata discovered from a template document.
 *
 * @param signatureAnchorKeys discovered signature anchor keys
 * @param dateAnchorKeys discovered date-signed anchor keys
 */
@Schema(description = "Parsed e-sign anchor metadata")
public record DocumentTemplateEsignAnchorMetadata(
    @Schema(description = "Discovered signature anchor keys") List<String> signatureAnchorKeys,
    @Schema(description = "Discovered date-signed anchor keys") List<String> dateAnchorKeys) {

  /** Canonical constructor that snapshots anchor-key lists. */
  public DocumentTemplateEsignAnchorMetadata {
    signatureAnchorKeys =
        signatureAnchorKeys == null ? List.of() : List.copyOf(signatureAnchorKeys);
    dateAnchorKeys = dateAnchorKeys == null ? List.of() : List.copyOf(dateAnchorKeys);
  }

  public boolean isEsignable() {
    return !signatureAnchorKeys.isEmpty();
  }
}
