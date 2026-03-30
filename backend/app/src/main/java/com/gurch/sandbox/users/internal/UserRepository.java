package com.gurch.sandbox.users.internal;

import com.gurch.sandbox.users.internal.models.UserEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends ListCrudRepository<UserEntity, Integer> {}
