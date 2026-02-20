package com.gurch.sandbox.auth.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("permissions")
public record PermissionEntity(
    @Id UUID id,
    String code,
    String description,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}
