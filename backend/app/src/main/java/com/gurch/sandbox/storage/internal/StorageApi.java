package com.gurch.sandbox.storage.internal;

import java.io.IOException;
import java.io.InputStream;

/** Internal API for raw binary object storage operations. */
interface StorageApi {

  /** Persists content and returns provider metadata plus storage key. */
  StorageWriteResult write(StorageWriteRequest request) throws IOException;

  /** Opens a stored object stream by storage key. */
  InputStream read(String storagePath) throws IOException;

  /** Deletes a stored object by storage key. */
  void delete(String storagePath) throws IOException;
}
