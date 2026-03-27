package com.gurch.sandbox.config.internal;

import com.gurch.sandbox.security.AuthenticatedUser;
import com.gurch.sandbox.security.AuthenticatedUserDetails;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/esign/envelopes/webhooks/docusign")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(httpBasic -> {})
        .formLogin(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  UserDetailsService userDetailsService() {
    Map<String, HardcodedUser> usersByUsername = new LinkedHashMap<>();
    List<HardcodedUser> users =
        List.of(
            user("system", "system", 1, null),
            user("alice", "alice", 2, 1),
            user("bob", "bob", 3, null));
    for (HardcodedUser user : users) {
      usersByUsername.put(user.username(), user);
    }
    return username -> {
      HardcodedUser user = usersByUsername.get(username);
      if (user == null) {
        throw new UsernameNotFoundException("Unknown user: " + username);
      }
      return user.toUserDetails();
    };
  }

  private static HardcodedUser user(
      String username, String password, Integer userId, Integer tenantId) {
    return new HardcodedUser(username, "{noop}" + password, userId, tenantId);
  }

  private record HardcodedUser(String username, String password, Integer userId, Integer tenantId) {
    private AuthenticatedUserDetails toUserDetails() {
      return new AuthenticatedUserDetails(
          new AuthenticatedUser(userId, tenantId),
          username,
          password,
          List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
  }
}
