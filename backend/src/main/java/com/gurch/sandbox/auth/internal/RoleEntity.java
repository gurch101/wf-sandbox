package com.gurch.sandbox.auth.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("roles")
public record RoleEntity(
    @Id UUID id, String code, String name, Long version, Instant createdAt, Instant updatedAt) {}
