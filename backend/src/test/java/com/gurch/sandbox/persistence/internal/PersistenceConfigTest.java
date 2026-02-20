package com.gurch.sandbox.persistence.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
