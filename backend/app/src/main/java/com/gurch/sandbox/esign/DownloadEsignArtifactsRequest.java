package com.gurch.sandbox.esign;

import lombok.Builder;
import lombok.Value;

/** Command payload for downloading signed artifacts. */
@Value
@Builder
public class DownloadEsignArtifactsRequest {
  String envelopeId;
}
