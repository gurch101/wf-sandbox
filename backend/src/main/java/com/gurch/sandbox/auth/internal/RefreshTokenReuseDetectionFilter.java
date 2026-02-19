package com.gurch.sandbox.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RefreshTokenReuseDetectionFilter extends OncePerRequestFilter {

  private final RefreshTokenFamilyRepository refreshTokenFamilyRepository;
  private final ObjectMapper objectMapper;

  public RefreshTokenReuseDetectionFilter(
      RefreshTokenFamilyRepository refreshTokenFamilyRepository, ObjectMapper objectMapper) {
    this.refreshTokenFamilyRepository = refreshTokenFamilyRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"/oauth2/token".equals(request.getRequestURI())
        || !"POST".equalsIgnoreCase(request.getMethod())
        || !"refresh_token".equals(request.getParameter("grant_type"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String refreshToken = request.getParameter("refresh_token");
    if (refreshToken == null || refreshToken.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    String hash = refreshTokenFamilyRepository.hashToken(refreshToken);
    var tokenRecord = refreshTokenFamilyRepository.findByTokenHash(hash);
    if (tokenRecord.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    UUID familyId = tokenRecord.get().familyId();
    boolean revoked =
        tokenRecord.get().revoked() || refreshTokenFamilyRepository.isFamilyRevoked(familyId);
    boolean reused = !tokenRecord.get().active();
    if (revoked || reused) {
      refreshTokenFamilyRepository.revokeFamily(familyId);
      writeInvalidGrant(response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void writeInvalidGrant(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(),
        java.util.Map.of(
            "error", "invalid_grant",
            "error_description", "Refresh token reuse detected"));
  }
}
