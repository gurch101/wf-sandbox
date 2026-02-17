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
  void shouldDocumentDraftUpdateValidationErrorCodesFromApiErrorEnum() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    RequestController controller = new RequestController(mock(RequestApi.class));
    Method updateMethod =
        RequestController.class.getMethod(
            "updateDraft", Long.class, RequestDtos.UpdateDraftRequest.class);
    HandlerMethod handlerMethod = new HandlerMethod(controller, updateMethod);

    Operation operation = new Operation();
    operation = config.apiErrorEnumCustomizer().customize(operation, handlerMethod);

    ApiResponse response400 = operation.getResponses().get("400");
    assertThat(response400).isNotNull();
    assertThat(response400.getDescription()).contains("INVALID_DRAFT_UPDATE_STATUS");
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
                "INVALID_DRAFT_UPDATE_STATUS",
                "message",
                "only DRAFT requests can be updated"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldDocumentDraftSubmitValidationErrorCodesFromApiErrorEnum() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    RequestController controller = new RequestController(mock(RequestApi.class));
    Method submitMethod =
        RequestController.class.getMethod(
            "submitDraft", Long.class, RequestDtos.UpdateDraftRequest.class);
    HandlerMethod handlerMethod = new HandlerMethod(controller, submitMethod);

    Operation operation = new Operation();
    operation = config.apiErrorEnumCustomizer().customize(operation, handlerMethod);

    ApiResponse response400 = operation.getResponses().get("400");
    assertThat(response400).isNotNull();
    assertThat(response400.getDescription()).contains("INVALID_DRAFT_SUBMIT_STATUS");
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
                "INVALID_DRAFT_SUBMIT_STATUS",
                "message",
                "only DRAFT requests can be submitted"));
  }
}
