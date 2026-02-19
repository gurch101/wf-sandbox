package com.gurch.sandbox.persistence;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/** Base entity containing created/updated audit metadata. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public abstract class AuditableEntity {
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
  @CreatedBy private UUID createdBy;
  @LastModifiedBy private UUID updatedBy;
}
