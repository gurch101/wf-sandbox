package com.gurch.sandbox.auth;

import com.gurch.sandbox.auth.internal.JdbcUserDetailsService;
import com.gurch.sandbox.auth.internal.RefreshTokenFamilyRepository;
import com.gurch.sandbox.auth.internal.RefreshTokenReuseDetectionFilter;
import com.gurch.sandbox.auth.internal.SystemClientUserRepository;
import com.gurch.sandbox.auth.internal.TrackingOAuth2AuthorizationService;
import com.gurch.sandbox.auth.internal.UserLookupRepository;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/** Spring Authorization Server wiring for OAuth2 flows and token customization. */
@Configuration
@EnableConfigurationProperties(JwtKeyProperties.class)
public class AuthorizationServerConfig {

  @Bean
  @Order(1)
  SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http,
      RefreshTokenReuseDetectionFilter refreshTokenReuseDetectionFilter,
      DaoAuthenticationProvider daoAuthenticationProvider)
      throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
        OAuth2AuthorizationServerConfigurer.authorizationServer();

    http.securityMatcher(
            new OrRequestMatcher(
                authorizationServerConfigurer.getEndpointsMatcher(),
                request ->
                    "/login".equals(request.getServletPath())
                        || "/login".equals(request.getRequestURI())))
        .csrf(AbstractHttpConfigurer::disable)
        .with(authorizationServerConfigurer, Customizer.withDefaults())
        .authenticationProvider(daoAuthenticationProvider)
        .formLogin(Customizer.withDefaults())
        .addFilterBefore(
            refreshTokenReuseDetectionFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());

    return http.build();
  }

  @Bean
  JWKSource<SecurityContext> jwkSource(FileSystemJwtKeyManager fileSystemJwtKeyManager) {
    List<RSAKey> keys = fileSystemJwtKeyManager.loadSigningKeys();
    return new ImmutableJWKSet<>(new JWKSet(keys.stream().map(key -> (JWK) key).toList()));
  }

  @Bean
  JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  @Bean
  AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().build();
  }

  @Bean
  OAuth2AuthorizationService oauth2AuthorizationService(
      RefreshTokenFamilyRepository refreshTokenFamilyRepository,
      UserLookupRepository userLookupRepository) {
    return new TrackingOAuth2AuthorizationService(
        new InMemoryOAuth2AuthorizationService(),
        refreshTokenFamilyRepository,
        userLookupRepository);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  DaoAuthenticationProvider daoAuthenticationProvider(
      JdbcUserDetailsService jdbcUserDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(jdbcUserDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> machineClientSubjectCustomizer(
      SystemClientUserRepository systemClientUserRepository,
      UserLookupRepository userLookupRepository) {
    return context -> {
      if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        return;
      }
      if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
        String clientId = context.getRegisteredClient().getClientId();
        UUID subject =
            systemClientUserRepository
                .findSystemUserIdByClientId(clientId)
                .orElseThrow(
                    () ->
                        new OAuth2AuthenticationException(
                            new OAuth2Error(
                                OAuth2ErrorCodes.INVALID_CLIENT,
                                "No mapped system user for client: " + clientId,
                                null)));
        context.getClaims().subject(subject.toString()).claim("token_type", "system");
        return;
      }

      String login = context.getPrincipal().getName();
      userLookupRepository
          .findEnabledUserIdByLogin(login)
          .ifPresent(
              userId ->
                  context
                      .getClaims()
                      .subject(userId.toString())
                      .claim("preferred_username", login)
                      .claim("token_type", "user"));
    };
  }
}
