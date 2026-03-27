package com.gurch.sandbox.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Public API for storage-backed binary objects and their persisted metadata. */
public interface StoredObjectApi {

  /** Stores a new object and persists its metadata record. */
  StoredObject store(StoreObjectRequest request);

  /** Finds a stored object by id without opening its content stream. */
  Optional<StoredObject> findById(Long id);

  /** Opens a stream for the stored object's content. */
  InputStream read(Long id) throws IOException;

  /** Deletes the stored object metadata and underlying content. */
  void delete(Long id) throws IOException;
}
