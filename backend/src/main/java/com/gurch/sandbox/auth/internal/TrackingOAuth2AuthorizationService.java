package com.gurch.sandbox.auth.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

public class TrackingOAuth2AuthorizationService implements OAuth2AuthorizationService {

  private static final String REFRESH_TOKEN_FAMILY_ID = "refresh_token_family_id";
  private final OAuth2AuthorizationService delegate;
  private final RefreshTokenFamilyRepository refreshTokenFamilyRepository;
  private final UserLookupRepository userLookupRepository;

  public TrackingOAuth2AuthorizationService(
      OAuth2AuthorizationService delegate,
      RefreshTokenFamilyRepository refreshTokenFamilyRepository,
      UserLookupRepository userLookupRepository) {
    this.delegate = delegate;
    this.refreshTokenFamilyRepository = refreshTokenFamilyRepository;
    this.userLookupRepository = userLookupRepository;
  }

  @Override
  public void save(OAuth2Authorization authorization) {
    OAuth2Authorization.Token<org.springframework.security.oauth2.core.OAuth2RefreshToken>
        refreshToken = authorization.getRefreshToken();
    if (refreshToken == null) {
      delegate.save(authorization);
      return;
    }

    OAuth2Authorization existing = delegate.findById(authorization.getId());
    String newHash =
        refreshTokenFamilyRepository.hashToken(refreshToken.getToken().getTokenValue());
    Instant expiresAt =
        Objects.requireNonNull(
            refreshToken.getToken().getExpiresAt(),
            "Refresh token expiry is required for refresh-family tracking");
    String principalName = authorization.getPrincipalName();
    String clientId = authorization.getRegisteredClientId();

    UUID userId =
        userLookupRepository
            .findEnabledUserIdByLogin(principalName)
            .orElseGet(
                () -> {
                  try {
                    return UUID.fromString(principalName);
                  } catch (Exception ignored) {
                    throw new IllegalStateException(
                        "Cannot resolve principal to user UUID for refresh tracking: "
                            + principalName);
                  }
                });

    UUID familyId =
        existing != null
            ? existing.getAttribute(REFRESH_TOKEN_FAMILY_ID)
            : refreshTokenFamilyRepository.createOrGetFamily(userId, clientId, expiresAt);
    if (familyId == null) {
      familyId = refreshTokenFamilyRepository.createOrGetFamily(userId, clientId, expiresAt);
    }

    String previousHash = null;
    OAuth2Authorization.Token<org.springframework.security.oauth2.core.OAuth2RefreshToken>
        existingRefreshToken = existing == null ? null : existing.getRefreshToken();
    if (existingRefreshToken != null && existingRefreshToken.getToken() != null) {
      previousHash =
          refreshTokenFamilyRepository.hashToken(existingRefreshToken.getToken().getTokenValue());
    }

    OAuth2Authorization trackedAuthorization =
        OAuth2Authorization.from(authorization)
            .attribute(REFRESH_TOKEN_FAMILY_ID, familyId)
            .build();
    delegate.save(trackedAuthorization);

    if (previousHash == null) {
      refreshTokenFamilyRepository.rotate(familyId, null, newHash, expiresAt);
    } else {
      refreshTokenFamilyRepository.rotate(familyId, previousHash, newHash, expiresAt);
    }
  }

  @Override
  public void remove(OAuth2Authorization authorization) {
    delegate.remove(authorization);
  }

  @Override
  public OAuth2Authorization findById(String id) {
    return delegate.findById(id);
  }

  @Override
  public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
    return delegate.findByToken(token, tokenType);
  }
}
