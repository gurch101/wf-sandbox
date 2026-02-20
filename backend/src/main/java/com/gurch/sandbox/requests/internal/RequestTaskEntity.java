package com.gurch.sandbox.requests.internal;

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
@Table("request_tasks")
public class RequestTaskEntity extends MutableEntity<Long> {
  private Long requestId;
  private String processInstanceId;
  private String taskId;
  private String name;
  private RequestTaskStatus status;
  private String assignee;
  private String action;
}
