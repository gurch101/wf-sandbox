package com.gurch.sandbox.requesttypes.internal.models;

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
@Table("request_types")
public class RequestTypeEntity extends MutableEntity<Long> {
  private String typeKey;
  private String name;
  private String description;
  private Long activeVersionId;
  private boolean active;
}
