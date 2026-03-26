package com.gurch.sandbox.config.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gurch.sandbox.security.AuthenticatedUserDetails;
import org.junit.jupiter.api.Test;

class SecurityConfigTest {

  private final SecurityConfig securityConfig = new SecurityConfig();

  @Test
  void shouldExposeHardcodedInMemoryUsers() {
    var userDetailsService = securityConfig.userDetailsService();

    var alice = userDetailsService.loadUserByUsername("alice");

    assertThat(alice).isInstanceOf(AuthenticatedUserDetails.class);
    assertThat(alice.getPassword()).isEqualTo("{noop}alice");
    assertThat(((AuthenticatedUserDetails) alice).authenticatedUser().userId()).isEqualTo(2);
    assertThat(((AuthenticatedUserDetails) alice).authenticatedUser().tenantId()).isEqualTo(1);
  }

  @Test
  void shouldReturnFreshUserDetailsOnEachLookup() {
    var userDetailsService = securityConfig.userDetailsService();

    var firstAlice = (AuthenticatedUserDetails) userDetailsService.loadUserByUsername("alice");
    firstAlice.eraseCredentials();
    var secondAlice = (AuthenticatedUserDetails) userDetailsService.loadUserByUsername("alice");

    assertThat(firstAlice.getPassword()).isNull();
    assertThat(secondAlice.getPassword()).isEqualTo("{noop}alice");
  }
}
