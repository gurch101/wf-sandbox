package com.gurch.sandbox.auth.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemClientUserRepository
    extends ListCrudRepository<SystemClientUserEntity, String> {
  Optional<SystemClientUserEntity> findByClientId(String clientId);

  default Optional<UUID> findSystemUserIdByClientId(String clientId) {
    return findByClientId(clientId).map(SystemClientUserEntity::userId);
  }
}
