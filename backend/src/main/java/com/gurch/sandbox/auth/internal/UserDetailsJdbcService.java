package com.gurch.sandbox.auth.internal;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsJdbcService implements UserDetailsService {

  private final UserCredentialRepository userCredentialRepository;

  public UserDetailsJdbcService(UserCredentialRepository userCredentialRepository) {
    this.userCredentialRepository = userCredentialRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
    UserCredentialRepository.UserCredentialRecord record =
        userCredentialRepository
            .findEnabledByLogin(usernameOrEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));

    return User.withUsername(record.username())
        .password(record.passwordHash())
        .roles("USER")
        .build();
  }
}
