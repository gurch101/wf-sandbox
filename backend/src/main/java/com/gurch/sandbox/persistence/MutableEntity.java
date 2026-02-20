package com.gurch.sandbox.persistence;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

/**
 * Generic mutable audit base for entities that track last updates.
 *
 * @param <ID> aggregate identifier type
 */
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class MutableEntity<ID> extends BaseEntity<ID> {
  @LastModifiedBy private Integer updatedBy;
  @LastModifiedDate private Instant updatedAt;
  @Version private Long version;
}
