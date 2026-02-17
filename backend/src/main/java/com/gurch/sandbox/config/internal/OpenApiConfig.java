package com.gurch.sandbox.config.internal;

import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.web.ApiErrorCode;
import com.gurch.sandbox.web.ApiErrorEnum;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
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

  @Bean
  public OperationCustomizer apiErrorEnumCustomizer() {
    return (operation, handlerMethod) -> {
      List<ApiErrorCode> errorCodes = resolveErrorCodes(handlerMethod);
      if (errorCodes.isEmpty()) {
        return operation;
      }

      Map<String, List<ApiErrorCode>> groupedByStatus =
          errorCodes.stream()
              .collect(
                  Collectors.groupingBy(
                      code -> String.valueOf(code.status().value()),
                      LinkedHashMap::new,
                      Collectors.toList()));

      groupedByStatus.forEach(
          (statusCode, statusErrorCodes) ->
              applyDocumentedErrors(operation, statusCode, statusErrorCodes));

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

  private static List<ApiErrorCode> resolveErrorCodes(HandlerMethod handlerMethod) {
    Set<Class<? extends ApiErrorCode>> errorEnumTypes = new LinkedHashSet<>();
    ApiErrorEnum classAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiErrorEnum.class);
    ApiErrorEnum methodAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiErrorEnum.class);

    if (classAnnotation != null) {
      addErrorEnumTypes(errorEnumTypes, classAnnotation.value());
    }

    if (methodAnnotation != null) {
      addErrorEnumTypes(errorEnumTypes, methodAnnotation.value());
    }

    List<ApiErrorCode> errorCodes = new ArrayList<>();
    for (Class<? extends ApiErrorCode> errorEnumType : errorEnumTypes) {
      if (!errorEnumType.isEnum()) {
        continue;
      }

      Object[] constants = errorEnumType.getEnumConstants();
      for (Object constant : constants) {
        errorCodes.add((ApiErrorCode) constant);
      }
    }
    return errorCodes;
  }

  private static void addErrorEnumTypes(
      Set<Class<? extends ApiErrorCode>> collector, Class<? extends ApiErrorCode>[] types) {
    collector.addAll(List.of(types));
  }

  private static void applyDocumentedErrors(
      Operation operation, String statusCode, List<ApiErrorCode> errorsForStatus) {
    ApiResponses responses =
        operation.getResponses() != null ? operation.getResponses() : new ApiResponses();
    operation.setResponses(responses);

    ApiResponse existing = responses.get(statusCode);
    ApiResponse response = existing != null ? existing : new ApiResponse();

    String summary =
        errorsForStatus.stream()
            .map(error -> "%s (%s): %s".formatted(error.code(), error.fieldName(), error.message()))
            .collect(Collectors.joining("; "));
    String description = "Documented validation/business errors: " + summary;
    response.setDescription(
        response.getDescription() == null || response.getDescription().isBlank()
            ? description
            : response.getDescription() + " " + description);

    List<Map<String, String>> errorCodeDetails =
        errorsForStatus.stream()
            .map(
                error ->
                    Map.of(
                        "fieldName",
                        error.fieldName(),
                        "code",
                        error.code(),
                        "message",
                        error.message()))
            .toList();
    response.addExtension("x-error-codes", errorCodeDetails);

    io.swagger.v3.oas.models.media.MediaType mediaType =
        new io.swagger.v3.oas.models.media.MediaType().schema(validationProblemSchema());
    io.swagger.v3.oas.models.media.Content content = new io.swagger.v3.oas.models.media.Content();
    content.addMediaType("application/problem+json", mediaType);
    response.setContent(content);

    responses.addApiResponse(statusCode, response);
  }

  private static Schema<?> validationProblemSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.addProperty("type", new StringSchema());
    schema.addProperty("title", new StringSchema());
    schema.addProperty("status", new Schema<>().type("integer").format("int32"));
    schema.addProperty("detail", new StringSchema());
    schema.addProperty("instance", new StringSchema());
    schema.addProperty(
        "errors",
        new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ValidationError")));
    return schema;
  }
}
