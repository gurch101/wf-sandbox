package com.gurch.sandbox.security;

import java.util.Optional;

/** Resolves user and tenant context from the active authentication. */
public interface CurrentUserProvider {

  /** Returns the authenticated user id when available. */
  Optional<Integer> currentUserId();

  /** Returns the authenticated tenant id when available. */
  Optional<Integer> currentTenantId();
}
