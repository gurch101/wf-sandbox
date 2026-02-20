package com.gurch.sandbox.requesttypes.internal;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestTypeVersionRepository
    extends ListCrudRepository<RequestTypeVersionEntity, Long> {
  Optional<RequestTypeVersionEntity> findByRequestTypeIdAndTypeVersion(
      Long requestTypeId, Integer typeVersion);
}
