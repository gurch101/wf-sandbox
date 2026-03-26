package com.gurch.sandbox.security;

import java.io.Serializable;

/**
 * Authenticated principal payload carrying user identity and tenant scope.
 *
 * @param userId authenticated user identifier
 * @param tenantId authenticated tenant identifier
 */
public record AuthenticatedUser(Integer userId, Integer tenantId) implements Serializable {}
