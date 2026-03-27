package com.gurch.sandbox.storage.internal;

import org.springframework.data.repository.ListCrudRepository;

interface StorageObjectRepository extends ListCrudRepository<StorageObjectEntity, Long> {}
