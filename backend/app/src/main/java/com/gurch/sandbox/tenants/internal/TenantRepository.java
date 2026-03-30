package com.gurch.sandbox.tenants.internal;

import com.gurch.sandbox.tenants.internal.models.TenantEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends ListCrudRepository<TenantEntity, Integer> {}
