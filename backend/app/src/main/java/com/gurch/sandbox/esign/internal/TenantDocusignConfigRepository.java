package com.gurch.sandbox.esign.internal;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface TenantDocusignConfigRepository
    extends ListCrudRepository<TenantDocusignConfigEntity, Long> {
  Optional<TenantDocusignConfigEntity> findByTenantIdAndActiveTrue(Integer tenantId);
}
