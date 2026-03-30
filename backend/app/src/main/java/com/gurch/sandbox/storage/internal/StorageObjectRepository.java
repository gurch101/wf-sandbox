package com.gurch.sandbox.storage.internal;

import com.gurch.sandbox.storage.internal.models.StorageObjectEntity;
import org.springframework.data.repository.ListCrudRepository;

interface StorageObjectRepository extends ListCrudRepository<StorageObjectEntity, Long> {}
