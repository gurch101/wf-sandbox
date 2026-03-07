package com.gurch.sandbox.config.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonConfigTest {

  @Test
  void shouldTreatEmptyEnumStringAsNull() throws Exception {
    JacksonConfig config = new JacksonConfig();
    Jackson2ObjectMapperBuilderCustomizer customizer = config.emptyEnumStringAsNullCustomizer();

    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    customizer.customize(builder);
    ObjectMapper mapper = builder.build();

    Payload payload = mapper.readValue("{\"status\":\"\"}", Payload.class);

    assertThat(payload.status).isNull();
  }

  private static final class Payload {
    TestEnum status;
  }

  private enum TestEnum {
    A,
    B
  }
}
