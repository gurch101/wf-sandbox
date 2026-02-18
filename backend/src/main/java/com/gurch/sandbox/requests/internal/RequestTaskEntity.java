package com.gurch.sandbox.requests.internal;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@Table("request_tasks")
public class RequestTaskEntity {
  @Id private Long id;
  private Long requestId;
  private String processInstanceId;
  private String taskId;
  private String name;
  private RequestTaskStatus status;
  private String assignee;
  private String action;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
}
