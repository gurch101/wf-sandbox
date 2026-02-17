package com.gurch.sandbox.storage;

import java.io.IOException;
import java.io.InputStream;

/** Public API for binary object storage operations. */
public interface StorageApi {

  /** Persists content and returns provider metadata plus storage key. */
  StorageWriteResult write(StorageWriteRequest request) throws IOException;

  /** Opens a stored object stream by storage key. */
  InputStream read(String storagePath) throws IOException;

  /** Deletes a stored object by storage key. */
  void delete(String storagePath) throws IOException;
}
