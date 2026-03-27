package com.gurch.sandbox.pdfutils;

import java.util.Set;

/**
 * Parsed e-sign anchor keys discovered in PDF text content.
 *
 * @param signatureAnchorKeys discovered signature anchor keys
 * @param dateAnchorKeys discovered date-signed anchor keys
 */
public record PdfAnchorParseResult(Set<String> signatureAnchorKeys, Set<String> dateAnchorKeys) {

  /** Canonical constructor that snapshots parsed anchor sets. */
  public PdfAnchorParseResult {
    signatureAnchorKeys = signatureAnchorKeys == null ? Set.of() : Set.copyOf(signatureAnchorKeys);
    dateAnchorKeys = dateAnchorKeys == null ? Set.of() : Set.copyOf(dateAnchorKeys);
  }
}
