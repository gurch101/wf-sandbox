package com.gurch.sandbox.tenants.internal;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends ListCrudRepository<TenantEntity, Integer> {}
