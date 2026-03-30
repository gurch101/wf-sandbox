package com.gurch.sandbox.storage.internal;

import com.gurch.sandbox.storage.StoredObjectApi;
import com.gurch.sandbox.storage.dto.StoreObjectRequest;
import com.gurch.sandbox.storage.dto.StoredObject;
import com.gurch.sandbox.storage.internal.models.StorageObjectEntity;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultStoredObjectService implements StoredObjectApi {

  private final StorageApi storageApi;
  private final StorageObjectRepository repository;

  @Override
  public StoredObject store(StoreObjectRequest request) {
    validateRequest(request);
    StorageWriteResult stored;
    try {
      stored =
          storageApi.write(
              StorageWriteRequest.builder()
                  .namespace(request.getNamespace())
                  .originalFilename(request.getFileName())
                  .contentStream(request.getContentStream())
                  .build());
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist stored object", e);
    }

    try {
      StorageObjectEntity entity =
          repository.save(
              StorageObjectEntity.builder()
                  .fileName(request.getFileName().trim())
                  .mimeType(request.getMimeType().trim())
                  .contentSize(stored.contentSize())
                  .checksumSha256(stored.checksumSha256())
                  .storageProvider(stored.provider())
                  .storagePath(stored.storagePath())
                  .build());
      return toStoredObject(entity);
    } catch (RuntimeException e) {
      try {
        storageApi.delete(stored.storagePath());
      } catch (IOException ignored) {
        // Metadata persistence failure is the root cause.
      }
      throw e;
    }
  }

  @Override
  public Optional<StoredObject> findById(Long id) {
    return repository.findById(id).map(this::toStoredObject);
  }

  @Override
  public InputStream read(Long id) throws IOException {
    StorageObjectEntity entity =
        repository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Stored object not found: " + id));
    return storageApi.read(entity.getStoragePath());
  }

  @Override
  public void delete(Long id) throws IOException {
    if (id == null) {
      return;
    }
    try {
      repository
          .findById(id)
          .ifPresent(
              entity -> {
                try {
                  storageApi.delete(entity.getStoragePath());
                } catch (IOException e) {
                  throw new StorageObjectDeletionException(e);
                }
                repository.delete(entity);
              });
    } catch (StorageObjectDeletionException e) {
      throw (IOException) e.getCause();
    }
  }

  private StoredObject toStoredObject(StorageObjectEntity entity) {
    return StoredObject.builder()
        .id(entity.getId())
        .fileName(entity.getFileName())
        .mimeType(entity.getMimeType())
        .contentSize(entity.getContentSize())
        .checksumSha256(entity.getChecksumSha256())
        .provider(entity.getStorageProvider())
        .storagePath(entity.getStoragePath())
        .build();
  }

  private static void validateRequest(StoreObjectRequest request) {
    if (request == null || request.getContentStream() == null) {
      throw new IllegalArgumentException("content stream is required");
    }
    if (request.getFileName() == null || request.getFileName().isBlank()) {
      throw new IllegalArgumentException("file name is required");
    }
    if (request.getMimeType() == null || request.getMimeType().isBlank()) {
      throw new IllegalArgumentException("mime type is required");
    }
  }

  private static final class StorageObjectDeletionException extends RuntimeException {
    private StorageObjectDeletionException(IOException cause) {
      super(cause);
    }
  }
}
