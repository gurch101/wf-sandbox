package com.gurch.sandbox.auth.internal;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("system_client_users")
public record SystemClientUserEntity(@Id String clientId, UUID userId) {}
