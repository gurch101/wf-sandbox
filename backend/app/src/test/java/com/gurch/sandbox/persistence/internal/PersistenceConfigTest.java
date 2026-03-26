package com.gurch.sandbox.persistence.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.security.CurrentUserProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

class PersistenceConfigTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldConvertJsonNodeToPgObject() {
    var converter = new PersistenceConfig.JsonNodeToPgObjectConverter(objectMapper);

    PGobject result = converter.convert(objectMapper.createObjectNode().put("amount", 10));

    assertThat(result.getType()).isEqualTo("jsonb");
    assertThat(result.getValue()).contains("\"amount\":10");
  }

  @Test
  void shouldConvertPgObjectToJsonNode() throws Exception {
    var converter = new PersistenceConfig.PgObjectToJsonNodeConverter(objectMapper);
    PGobject source = new PGobject();
    source.setType("jsonb");
    source.setValue("{\"name\":\"x\"}");

    var result = converter.convert(source);

    assertThat(result.get("name").asText()).isEqualTo("x");
  }

  @Test
  void shouldReturnEmptyAuditorWhenNoAuthenticatedUserExists() {
    CurrentUserProvider currentUserProvider =
        new CurrentUserProvider() {
          @Override
          public Optional<Integer> currentUserId() {
            return Optional.empty();
          }

          @Override
          public Optional<Integer> currentTenantId() {
            return Optional.empty();
          }
        };

    var config = new PersistenceConfig(objectMapper, currentUserProvider);

    assertThat(config.jdbcAuditorAware().getCurrentAuditor()).isEmpty();
  }

  @Test
  void shouldUseAuthenticatedAuditorWhenAvailable() {
    CurrentUserProvider currentUserProvider =
        new CurrentUserProvider() {
          @Override
          public Optional<Integer> currentUserId() {
            return Optional.of(42);
          }

          @Override
          public Optional<Integer> currentTenantId() {
            return Optional.of(7);
          }
        };

    var config = new PersistenceConfig(objectMapper, currentUserProvider);

    assertThat(config.jdbcAuditorAware().getCurrentAuditor()).contains(42);
  }
}
