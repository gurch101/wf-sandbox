package com.gurch.sandbox.policies.internal;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface PolicyInputCatalogRepository extends ListCrudRepository<PolicyInputCatalogEntity, Long> {
  List<PolicyInputCatalogEntity> findAllByPolicyVersionIdOrderByFieldKey(Long policyVersionId);
}
