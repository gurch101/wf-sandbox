package com.gurch.sandbox.security;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring Security principal carrying the authenticated sandbox user and tenant context. */
public class AuthenticatedUserDetails extends User {

  @Serial private static final long serialVersionUID = 1L;

  private final AuthenticatedUser authenticatedUser;

  /**
   * Creates a principal that preserves sandbox user and tenant identity alongside Spring Security
   * credentials and authorities.
   */
  public AuthenticatedUserDetails(
      AuthenticatedUser authenticatedUser,
      String username,
      String password,
      Collection<? extends GrantedAuthority> authorities) {
    super(username, password, authorities);
    this.authenticatedUser = authenticatedUser;
  }

  /** Returns the authenticated sandbox user payload associated with this principal. */
  public AuthenticatedUser authenticatedUser() {
    return authenticatedUser;
  }

  /** Compares both the Spring Security user state and the sandbox identity payload. */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AuthenticatedUserDetails that)) {
      return false;
    }
    return super.equals(other) && Objects.equals(authenticatedUser, that.authenticatedUser);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), authenticatedUser);
  }
}
