package com.gurch.sandbox.persistence;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;

/**
 * Generic immutable audit base for all entities.
 *
 * @param <ID> aggregate identifier type
 */
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode
public class BaseEntity<ID> {
  @Id private ID id;
  @CreatedBy private Integer createdBy;
  @CreatedDate private Instant createdAt;
}
