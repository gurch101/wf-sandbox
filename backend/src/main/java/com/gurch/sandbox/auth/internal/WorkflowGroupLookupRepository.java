package com.gurch.sandbox.auth.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowGroupLookupRepository
    extends ListCrudRepository<WorkflowGroupEntity, UUID> {
  Optional<WorkflowGroupEntity> findByCode(String code);
}
