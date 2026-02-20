package com.gurch.sandbox.requests.internal;

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
@Table("request_tasks")
public class RequestTaskEntity extends VersionedAuditableEntity<Long> {
  private Long requestId;
  private String processInstanceId;
  private String taskId;
  private String name;
  private RequestTaskStatus status;
  private String assignee;
  private String action;
}
