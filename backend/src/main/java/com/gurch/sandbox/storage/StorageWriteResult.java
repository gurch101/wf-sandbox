package com.gurch.sandbox.storage;

/**
 * Storage write outcome with provider and persisted object metadata.
 *
 * @param provider storage provider used for persistence
 * @param storagePath provider-specific storage path
 * @param contentSize stored content size in bytes
 * @param checksumSha256 SHA-256 checksum of stored bytes
 */
public record StorageWriteResult(
    StorageProviderType provider, String storagePath, long contentSize, String checksumSha256) {}
