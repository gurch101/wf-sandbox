package com.gurch.sandbox.policies.internal;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface PolicyOutputContractRepository
    extends ListCrudRepository<PolicyOutputContractEntity, Long> {
  Optional<PolicyOutputContractEntity> findByPolicyVersionId(Long policyVersionId);
}
