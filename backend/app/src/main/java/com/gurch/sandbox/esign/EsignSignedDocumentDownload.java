package com.gurch.sandbox.esign;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

/** Download payload for a stored signed PDF. */
@Value
@Builder
public class EsignSignedDocumentDownload {
  String fileName;
  String mimeType;
  long contentSize;
  InputStream contentStream;
}
