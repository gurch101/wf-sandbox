package com.gurch.sandbox.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

/**
 * Base auditable entity that also includes id and optimistic lock version.
 *
 * @param <T> id type
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public abstract class VersionedAuditableEntity<T> extends AuditableEntity {
  @Id private T id;
  @Version private Long version;
}
