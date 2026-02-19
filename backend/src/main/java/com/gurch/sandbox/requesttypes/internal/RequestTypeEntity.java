package com.gurch.sandbox.requesttypes.internal;

import com.gurch.sandbox.persistence.VersionedAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Table("request_types")
public class RequestTypeEntity extends VersionedAuditableEntity {
  private String typeKey;
  private String name;
  private String description;
  private Long activeVersionId;
  private boolean active;
}
