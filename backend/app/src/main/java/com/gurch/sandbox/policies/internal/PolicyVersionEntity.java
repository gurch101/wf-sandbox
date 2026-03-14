package com.gurch.sandbox.policies.internal;

import com.gurch.sandbox.persistence.MutableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("policy_versions")
class PolicyVersionEntity extends MutableEntity<Long> {
  private Long requestTypeId;
  private Integer policyVersion;
  private PolicyVersionStatus status;
}
