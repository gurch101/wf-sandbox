package com.gurch.sandbox.auth;

import com.gurch.sandbox.auth.internal.AuthContextRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthContextRepository authContextRepository;

  public AuthController(AuthContextRepository authContextRepository) {
    this.authContextRepository = authContextRepository;
  }

  @GetMapping("/me")
  public AuthDtos.MeResponse me(Authentication authentication) {
    String userId = authentication.getName();
    String username = extractUsername(authentication);
    List<String> roles = extractRoles(authentication);
    List<String> permissions = extractPermissions(authentication);
    Optional<UUID> principalUserId = parseUuid(userId);
    List<String> workflowGroupIds =
        principalUserId.map(authContextRepository::findWorkflowGroupCodes).orElseGet(List::of);
    List<String> clientScopeIds =
        principalUserId.map(authContextRepository::findClientScopeIds).orElseGet(List::of);
    return new AuthDtos.MeResponse(
        userId, username, roles, permissions, workflowGroupIds, clientScopeIds);
  }

  private String extractUsername(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      String preferredUsername =
          jwtAuthenticationToken.getToken().getClaimAsString("preferred_username");
      if (preferredUsername != null && !preferredUsername.isBlank()) {
        return preferredUsername;
      }
    }
    return authentication.getName();
  }

  private List<String> extractRoles(Authentication authentication) {
    Set<String> roles = new TreeSet<>();
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      String value = authority.getAuthority();
      if (value.startsWith("ROLE_")) {
        roles.add(value.substring("ROLE_".length()));
      }
    }
    return roles.stream().toList();
  }

  private List<String> extractPermissions(Authentication authentication) {
    Set<String> permissions = new TreeSet<>();
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      String value = authority.getAuthority();
      if (!value.startsWith("ROLE_")) {
        permissions.add(value);
      }
    }
    return permissions.stream().toList();
  }

  private Optional<UUID> parseUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }
}
