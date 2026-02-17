package com.gurch.sandbox.requesttypes.internal;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@Table("request_types")
public class RequestTypeEntity {
  @Id private Long id;
  private String typeKey;
  private String name;
  private String description;
  private Long activeVersionId;
  private boolean active;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
  @Version private Long version;
}
