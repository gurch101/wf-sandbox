package com.gurch.sandbox.esign.internal;

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
@Table("tenant_docusign_configs")
public class TenantDocusignConfigEntity extends MutableEntity<Long> {
  private Integer tenantId;
  private String basePath;
  private String accountId;
  private String authToken;
  private boolean active;
}
