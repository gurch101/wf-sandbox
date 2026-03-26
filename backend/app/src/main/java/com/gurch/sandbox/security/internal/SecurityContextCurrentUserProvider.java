package com.gurch.sandbox.security.internal;

import com.gurch.sandbox.security.AuthenticatedUser;
import com.gurch.sandbox.security.AuthenticatedUserDetails;
import com.gurch.sandbox.security.CurrentUserProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
class SecurityContextCurrentUserProvider implements CurrentUserProvider {

  @Override
  public Optional<Integer> currentUserId() {
    return currentUser().map(AuthenticatedUser::userId);
  }

  @Override
  public Optional<Integer> currentTenantId() {
    return currentUser().map(AuthenticatedUser::tenantId);
  }

  private Optional<AuthenticatedUser> currentUser() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .filter(Authentication::isAuthenticated)
        .flatMap(this::resolvePrincipal);
  }

  private Optional<AuthenticatedUser> resolvePrincipal(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof AuthenticatedUser authenticatedUser) {
      return Optional.of(authenticatedUser);
    }
    if (principal instanceof AuthenticatedUserDetails authenticatedUserDetails) {
      return Optional.of(authenticatedUserDetails.authenticatedUser());
    }
    if (principal instanceof UserDetails userDetails) {
      return parseUserId(userDetails.getUsername())
          .map(userId -> new AuthenticatedUser(userId, null));
    }
    return parseUserId(authentication.getName()).map(userId -> new AuthenticatedUser(userId, null));
  }

  private Optional<Integer> parseUserId(String principalName) {
    if (principalName == null || principalName.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Integer.parseInt(principalName));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }
}
