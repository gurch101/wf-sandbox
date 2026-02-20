package com.gurch.sandbox.auth.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLookupRepository extends ListCrudRepository<AuthUserEntity, UUID> {
  Optional<AuthUserEntity> findByUsernameAndEnabledTrue(String username);

  Optional<AuthUserEntity> findByEmailAndEnabledTrue(String email);

  Optional<AuthUserEntity> findByUsername(String username);

  default Optional<UUID> findEnabledUserIdByLogin(String login) {
    return findByUsernameAndEnabledTrue(login)
        .or(() -> findByEmailAndEnabledTrue(login))
        .map(AuthUserEntity::id);
  }
}
