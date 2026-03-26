package com.gurch.sandbox.security.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gurch.sandbox.security.AuthenticatedUser;
import com.gurch.sandbox.security.AuthenticatedUserDetails;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextCurrentUserProviderTest {

  private final SecurityContextCurrentUserProvider provider =
      new SecurityContextCurrentUserProvider();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldResolveUserAndTenantFromAuthenticatedUserDetailsPrincipal() {
    AuthenticatedUserDetails principal =
        new AuthenticatedUserDetails(
            new AuthenticatedUser(42, 7),
            "admin",
            "{noop}secret",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, principal.getPassword(), principal.getAuthorities()));

    assertThat(provider.currentUserId()).contains(42);
    assertThat(provider.currentTenantId()).contains(7);
  }
}
