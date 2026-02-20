package com.gurch.sandbox.auth.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record AuthUserEntity(
    @Id UUID id,
    String username,
    String email,
    boolean enabled,
    boolean isSystem,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt) {}
