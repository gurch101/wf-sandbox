package com.gurch.sandbox.config.internal;

import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.web.ValidationErrorCode;
import com.gurch.sandbox.web.ValidationErrorEnum;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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

  private static final String BASIC_AUTH_SCHEME = "basicAuth";
  private static final String APPLICATION_JSON = "application/json";
  private static final String IDEMPOTENCY_KEY_PARAMETER = "IdempotencyKey";

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    BASIC_AUTH_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("HTTP Basic authentication"))
                .addParameters(
                    IDEMPOTENCY_KEY_PARAMETER,
                    new HeaderParameter()
                        .name("Idempotency-Key")
                        .description("Unique key to ensure request idempotency")
                        .required(true)
                        .schema(new StringSchema())));
  }

  @Bean
  public OperationCustomizer idempotencyKeyHeaderCustomizer() {
    return (operation, handlerMethod) -> {
      if (!isIdempotentMethod(handlerMethod)) {
        return operation;
      }
      if (operation.getParameters() == null) {
        operation.setParameters(new ArrayList<>());
      }
      boolean alreadyPresent =
          operation.getParameters().stream()
              .anyMatch(
                  parameter ->
                      "Idempotency-Key".equals(parameter.getName())
                          || ("#/components/parameters/" + IDEMPOTENCY_KEY_PARAMETER)
                              .equals(parameter.get$ref()));
      if (!alreadyPresent) {
        operation.addParametersItem(
            new Parameter().$ref("#/components/parameters/" + IDEMPOTENCY_KEY_PARAMETER));
      }
      return operation;
    };
  }

  @Bean
  public OperationCustomizer basicAuthCustomizer() {
    return (operation, handlerMethod) -> {
      if (isAnonymousOperation(handlerMethod)) {
        operation.setSecurity(List.of());
        return operation;
      }
      if (operation.getSecurity() == null) {
        operation.setSecurity(new ArrayList<>());
      }
      boolean alreadyPresent =
          operation.getSecurity().stream()
              .anyMatch(requirement -> requirement.containsKey(BASIC_AUTH_SCHEME));
      if (!alreadyPresent) {
        operation.addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH_SCHEME));
      }
      return operation;
    };
  }

  @Bean
  public OperationCustomizer multipartJsonRequestPartCustomizer() {
    return (operation, handlerMethod) -> {
      if (operation.getRequestBody() == null
          || operation.getRequestBody().getContent() == null
          || operation.getRequestBody().getContent().get("multipart/form-data") == null) {
        return operation;
      }

      MediaType multipartMediaType =
          operation.getRequestBody().getContent().get("multipart/form-data");
      Schema<?> schema = multipartMediaType.getSchema();
      if (schema == null
          || schema.getProperties() == null
          || !schema.getProperties().containsKey("request")) {
        return operation;
      }

      if (multipartMediaType.getEncoding() == null) {
        multipartMediaType.setEncoding(new LinkedHashMap<>());
      }
      multipartMediaType
          .getEncoding()
          .computeIfAbsent("request", ignored -> new Encoding())
          .setContentType(APPLICATION_JSON);
      return operation;
    };
  }

  @Bean
  public OperationCustomizer validationErrorEnumCustomizer() {
    return (operation, handlerMethod) -> {
      List<ValidationErrorCode> errorCodes = resolveErrorCodes(handlerMethod);
      if (errorCodes.isEmpty()) {
        return operation;
      }

      applyDocumentedErrors(operation, errorCodes);

      return operation;
    };
  }

  private boolean isAnonymousOperation(HandlerMethod handlerMethod) {
    return handlerMethod.getBeanType().getName().equals("com.gurch.sandbox.esign.EsignController")
        && handlerMethod.getMethod().getName().equals("webhook");
  }

  private boolean isIdempotentMethod(HandlerMethod handlerMethod) {
    if (handlerMethod.hasMethodAnnotation(NotIdempotent.class)) {
      return false;
    }

    return handlerMethod.hasMethodAnnotation(PostMapping.class)
        || handlerMethod.hasMethodAnnotation(PutMapping.class)
        || handlerMethod.hasMethodAnnotation(DeleteMapping.class);
  }

  private static List<ValidationErrorCode> resolveErrorCodes(HandlerMethod handlerMethod) {
    Set<Class<? extends ValidationErrorCode>> errorEnumTypes = new LinkedHashSet<>();
    ValidationErrorEnum classAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(
            handlerMethod.getBeanType(), ValidationErrorEnum.class);
    ValidationErrorEnum methodAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(
            handlerMethod.getMethod(), ValidationErrorEnum.class);

    if (classAnnotation != null) {
      addErrorEnumTypes(errorEnumTypes, classAnnotation.value());
    }

    if (methodAnnotation != null) {
      addErrorEnumTypes(errorEnumTypes, methodAnnotation.value());
    }

    List<ValidationErrorCode> errorCodes = new ArrayList<>();
    for (Class<? extends ValidationErrorCode> errorEnumType : errorEnumTypes) {
      if (!errorEnumType.isEnum()) {
        continue;
      }

      Object[] constants = errorEnumType.getEnumConstants();
      for (Object constant : constants) {
        errorCodes.add((ValidationErrorCode) constant);
      }
    }
    return errorCodes;
  }

  private static void addErrorEnumTypes(
      Set<Class<? extends ValidationErrorCode>> collector,
      Class<? extends ValidationErrorCode>[] types) {
    collector.addAll(List.of(types));
  }

  private static void applyDocumentedErrors(
      Operation operation, List<ValidationErrorCode> errorsForStatus) {
    ApiResponses responses =
        operation.getResponses() != null ? operation.getResponses() : new ApiResponses();
    operation.setResponses(responses);

    ApiResponse existing = responses.get("400");
    ApiResponse response = existing != null ? existing : new ApiResponse();

    String summary =
        errorsForStatus.stream()
            .map(
                error ->
                    "%s (%s): %s"
                        .formatted(error.getCode(), error.getFieldName(), error.getMessage()))
            .collect(Collectors.joining("; "));
    String description = "Documented validation errors: " + summary;
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
                        error.getFieldName(),
                        "code",
                        error.getCode(),
                        "message",
                        error.getMessage()))
            .toList();
    response.addExtension("x-error-codes", errorCodeDetails);

    MediaType mediaType = new MediaType().schema(validationProblemSchema());
    Content content = new Content();
    content.addMediaType("application/problem+json", mediaType);
    response.setContent(content);

    responses.addApiResponse("400", response);
  }

  private static Schema<?> validationProblemSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.addProperty("type", new StringSchema());
    schema.addProperty("title", new StringSchema());
    schema.addProperty("status", new Schema<>().type("integer").format("int32"));
    schema.addProperty("detail", new StringSchema());
    schema.addProperty("instance", new StringSchema());
    schema.addProperty("errors", new ArraySchema().items(validationErrorSchema()));
    return schema;
  }

  private static Schema<?> validationErrorSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.description("Validation error details");
    schema.addProperty("name", new StringSchema().description("Name of the invalid field"));
    schema.addProperty("code", new StringSchema().description("Validation error code"));
    schema.addProperty(
        "message", new StringSchema().description("Reason for the validation failure"));
    return schema;
  }
}
