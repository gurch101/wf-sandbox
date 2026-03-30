package com.gurch.sandbox.storage.dto;

import com.gurch.sandbox.storage.StorageProviderType;
import lombok.Builder;

/**
 * Persisted binary object metadata managed by the storage module.
 *
 * @param id internal storage object identifier
 * @param fileName original or generated file name
 * @param mimeType content MIME type
 * @param contentSize stored content size in bytes
 * @param checksumSha256 SHA-256 checksum of stored bytes
 * @param provider storage provider used for persistence
 * @param storagePath provider-specific storage path
 */
@Builder
public record StoredObject(
    Long id,
    String fileName,
    String mimeType,
    long contentSize,
    String checksumSha256,
    StorageProviderType provider,
    String storagePath) {}
