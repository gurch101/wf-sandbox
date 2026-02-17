package com.gurch.sandbox.requesttypes.internal;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestTypeRepository extends ListCrudRepository<RequestTypeEntity, Long> {
  Optional<RequestTypeEntity> findByTypeKey(String typeKey);
}
