package com.gurch.sandbox.esign;

import lombok.Builder;
import lombok.Value;

/** Signed document artifacts retrieved from DocuSign. */
@Value
@Builder
public class DownloadEsignArtifactsResponse {
  byte[] signedDocumentPdf;
  byte[] certificatePdf;
}
