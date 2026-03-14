package com.gurch.sandbox.policies.internal;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface PolicyVersionRepository extends ListCrudRepository<PolicyVersionEntity, Long> {
  Optional<PolicyVersionEntity> findByRequestTypeIdAndPolicyVersion(
      Long requestTypeId, Integer policyVersion);
}
