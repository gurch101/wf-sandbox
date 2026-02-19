package com.gurch.sandbox.auth.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("workflow_groups")
public record WorkflowGroupEntity(
    @Id UUID id, String code, String name, Instant createdAt, Instant updatedAt) {}
