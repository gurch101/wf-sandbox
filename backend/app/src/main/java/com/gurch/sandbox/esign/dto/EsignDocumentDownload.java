package com.gurch.sandbox.esign.dto;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

/** Download payload for a stored e-sign document artifact. */
@Value
@Builder
public class EsignDocumentDownload {
  String fileName;
  String mimeType;
  long contentSize;
  InputStream contentStream;
}
