package com.gurch.sandbox.config.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJdbcAuditing
@RequiredArgsConstructor
public class PersistenceConfig {

  private final ObjectMapper objectMapper;
  private static final UUID SYSTEM_ACTOR_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  @Bean
  public JdbcCustomConversions jdbcCustomConversions() {
    return new JdbcCustomConversions(
        List.of(
            new JsonNodeToPgObjectConverter(objectMapper),
            new PgObjectToJsonNodeConverter(objectMapper)));
  }

  @Bean
  public AuditorAware<UUID> auditorAware() {
    return () -> Optional.of(resolveAuditor());
  }

  private UUID resolveAuditor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return SYSTEM_ACTOR_ID;
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof UUID uuidPrincipal) {
      return uuidPrincipal;
    }
    if (principal instanceof String principalName) {
      return parseUuidOrSystem(principalName);
    }
    return parseUuidOrSystem(authentication.getName());
  }

  private UUID parseUuidOrSystem(String value) {
    try {
      return UUID.fromString(value);
    } catch (Exception ignored) {
      return SYSTEM_ACTOR_ID;
    }
  }

  @WritingConverter
  @RequiredArgsConstructor
  public static class JsonNodeToPgObjectConverter implements Converter<JsonNode, PGobject> {
    private final ObjectMapper objectMapper;

    @Override
    public PGobject convert(JsonNode source) {
      try {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(objectMapper.writeValueAsString(source));
        return pgObject;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @ReadingConverter
  @RequiredArgsConstructor
  public static class PgObjectToJsonNodeConverter implements Converter<PGobject, JsonNode> {
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode convert(PGobject source) {
      try {
        return objectMapper.readTree(source.getValue());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
