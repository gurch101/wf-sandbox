package com.gurch.sandbox.storage;

/** Storage write outcome with provider and persisted object metadata. */
public record StorageWriteResult(
    StorageProviderType provider, String storagePath, long contentSize, String checksumSha256) {}
