package com.gurch.sandbox.persistence.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

@Configuration
@EnableJdbcAuditing(
    auditorAwareRef = "jdbcAuditorAware",
    dateTimeProviderRef = "jdbcAuditingDateTimeProvider")
@RequiredArgsConstructor
public class PersistenceConfig {

  private final ObjectMapper objectMapper;
  private final CurrentUserProvider currentUserProvider;

  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }

  @Bean
  public DateTimeProvider jdbcAuditingDateTimeProvider(Clock utcClock) {
    return () -> Optional.of(Instant.now(utcClock));
  }

  @Bean
  public AuditorAware<Integer> jdbcAuditorAware() {
    return currentUserProvider::currentUserId;
  }

  @Bean
  public JdbcCustomConversions jdbcCustomConversions() {
    return new JdbcCustomConversions(
        List.of(
            new JsonNodeToPgObjectConverter(objectMapper),
            new PgObjectToJsonNodeConverter(objectMapper)));
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
