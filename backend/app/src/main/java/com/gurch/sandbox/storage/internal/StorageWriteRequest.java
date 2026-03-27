package com.gurch.sandbox.storage.internal;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

/** Command for writing one binary object into storage. */
@Value
@Builder
public class StorageWriteRequest {
  String namespace;
  String originalFilename;
  InputStream contentStream;
}
