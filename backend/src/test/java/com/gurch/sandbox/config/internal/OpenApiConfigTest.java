package com.gurch.sandbox.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestController;
import com.gurch.sandbox.requests.RequestDtos;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class OpenApiConfigTest {

  @Test
  @SuppressWarnings("unchecked")
  void shouldDocumentCreateValidationErrorCodesFromApiErrorEnum() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    RequestController controller = new RequestController(mock(RequestApi.class));
    Method createMethod =
        RequestController.class.getMethod("create", RequestDtos.CreateRequest.class);
    HandlerMethod handlerMethod = new HandlerMethod(controller, createMethod);

    Operation operation = new Operation();
    operation = config.apiErrorEnumCustomizer().customize(operation, handlerMethod);

    ApiResponse response400 = operation.getResponses().get("400");
    assertThat(response400).isNotNull();
    assertThat(response400.getDescription()).contains("INVALID_CREATE_STATUS");
    assertThat(response400.getContent()).containsKey("application/problem+json");
    assertThat(response400.getExtensions()).containsKey("x-error-codes");

    List<Map<String, Object>> errorCodes =
        (List<Map<String, Object>>) response400.getExtensions().get("x-error-codes");
    assertThat(errorCodes)
        .contains(
            Map.of(
                "fieldName",
                "status",
                "code",
                "INVALID_CREATE_STATUS",
                "message",
                "status must be one of [DRAFT, SUBMITTED] for create requests"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldDocumentUpdateValidationErrorCodesFromApiErrorEnum() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    RequestController controller = new RequestController(mock(RequestApi.class));
    Method updateMethod =
        RequestController.class.getMethod("update", Long.class, RequestDtos.UpdateRequest.class);
    HandlerMethod handlerMethod = new HandlerMethod(controller, updateMethod);

    Operation operation = new Operation();
    operation = config.apiErrorEnumCustomizer().customize(operation, handlerMethod);

    ApiResponse response400 = operation.getResponses().get("400");
    assertThat(response400).isNotNull();
    assertThat(response400.getDescription()).contains("INVALID_UPDATE_STATUS_TRANSITION");
    assertThat(response400.getContent()).containsKey("application/problem+json");
    assertThat(response400.getExtensions()).containsKey("x-error-codes");

    List<Map<String, Object>> errorCodes =
        (List<Map<String, Object>>) response400.getExtensions().get("x-error-codes");
    assertThat(errorCodes)
        .contains(
            Map.of(
                "fieldName",
                "status",
                "code",
                "INVALID_UPDATE_STATUS_TRANSITION",
                "message",
                "status transition must remain DRAFT or move DRAFT -> SUBMITTED for update requests"));
  }
}
