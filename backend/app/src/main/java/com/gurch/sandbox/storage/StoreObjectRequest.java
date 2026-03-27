package com.gurch.sandbox.storage;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

/** Request to persist a new binary object and register it in the storage module. */
@Value
@Builder
public class StoreObjectRequest {
  String namespace;
  String fileName;
  String mimeType;
  InputStream contentStream;
}
