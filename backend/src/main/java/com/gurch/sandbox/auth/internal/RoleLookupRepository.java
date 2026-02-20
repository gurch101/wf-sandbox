package com.gurch.sandbox.auth.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleLookupRepository extends ListCrudRepository<RoleEntity, UUID> {
  Optional<RoleEntity> findByCode(String code);
}
