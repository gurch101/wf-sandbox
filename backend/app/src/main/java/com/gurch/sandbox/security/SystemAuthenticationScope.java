package com.gurch.sandbox.security;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Runs work under an explicit system authentication context for system-to-system flows. */
@Component
public class SystemAuthenticationScope {

  private static final Integer SYSTEM_USER_ID = 1;

  /**
   * Runs the supplied action with the system user installed in the security context.
   *
   * @param action work to execute under the temporary system authentication
   */
  public void run(Runnable action) {
    SecurityContext previous = SecurityContextHolder.getContext();
    SecurityContext systemContext = SecurityContextHolder.createEmptyContext();
    systemContext.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedUser(SYSTEM_USER_ID, null), null, List.of()));
    SecurityContextHolder.setContext(systemContext);
    try {
      action.run();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }
}
