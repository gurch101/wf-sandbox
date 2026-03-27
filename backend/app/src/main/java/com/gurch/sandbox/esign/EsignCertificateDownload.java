package com.gurch.sandbox.esign;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

/** Download payload for a stored signing certificate. */
@Value
@Builder
public class EsignCertificateDownload {
  String fileName;
  String mimeType;
  long contentSize;
  InputStream contentStream;
}
