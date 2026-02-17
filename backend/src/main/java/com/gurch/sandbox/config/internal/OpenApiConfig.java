package com.gurch.sandbox.config.internal;

import com.gurch.sandbox.idempotency.NotIdempotent;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfig {

  @Bean
  public OperationCustomizer idempotencyKeyHeaderCustomizer() {
    return (operation, handlerMethod) -> {
      if (isIdempotentMethod(handlerMethod)) {
        operation.addParametersItem(
            new HeaderParameter()
                .name("Idempotency-Key")
                .description("Unique key to ensure request idempotency")
                .required(true)
                .schema(new StringSchema()));
      }
      return operation;
    };
  }

  private boolean isIdempotentMethod(HandlerMethod handlerMethod) {
    if (handlerMethod.hasMethodAnnotation(NotIdempotent.class)) {
      return false;
    }

    return handlerMethod.hasMethodAnnotation(PostMapping.class)
        || handlerMethod.hasMethodAnnotation(PutMapping.class)
        || handlerMethod.hasMethodAnnotation(DeleteMapping.class);
  }
}
