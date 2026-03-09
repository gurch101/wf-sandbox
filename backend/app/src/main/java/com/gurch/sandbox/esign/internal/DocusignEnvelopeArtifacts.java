package com.gurch.sandbox.esign.internal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocusignEnvelopeArtifacts {
  byte[] signedDocumentPdf;
  byte[] certificatePdf;
}
