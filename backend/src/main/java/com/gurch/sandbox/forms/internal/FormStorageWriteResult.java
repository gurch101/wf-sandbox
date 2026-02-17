package com.gurch.sandbox.forms.internal;

import com.gurch.sandbox.forms.FormStorageProviderType;

/** Storage write outcome containing provider and persisted path key. */
public record FormStorageWriteResult(FormStorageProviderType provider, String storagePath) {}
