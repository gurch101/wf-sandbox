package com.gurch.sandbox.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestController;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.requests.internal.RequestAuthorization;
import com.gurch.sandbox.web.ApiErrorEnum;
import io.swagger.v3.oas.models.Operation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.method.HandlerMethod;

class OpenApiConfigTest {

  @Test
  void shouldLeaveOperationUnchangedWhenApiErrorEnumIsMissing() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    RequestController controller =
        new RequestController(mock(RequestApi.class), mock(RequestAuthorization.class));
    Method method =
        RequestController.class.getMethod(
            "getById", Long.class, org.springframework.security.core.Authentication.class);
    HandlerMethod handlerMethod = new HandlerMethod(controller, method);

    Operation operation = new Operation();
    Operation customized = config.apiErrorEnumCustomizer().customize(operation, handlerMethod);

    assertThat(customized.getResponses()).isNull();
  }

  @Test
  void shouldAddIdempotencyHeaderForPostMappings() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    HandlerMethod handlerMethod =
        new HandlerMethod(new DummyController(), DummyController.class.getMethod("idempotent"));

    Operation customized =
        config.idempotencyKeyHeaderCustomizer().customize(new Operation(), handlerMethod);

    assertThat(customized.getParameters()).isNotEmpty();
    assertThat(customized.getParameters().getFirst().getName()).isEqualTo("Idempotency-Key");
  }

  @Test
  void shouldSkipIdempotencyHeaderForNotIdempotentMethods() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    HandlerMethod handlerMethod =
        new HandlerMethod(new DummyController(), DummyController.class.getMethod("notIdempotent"));

    Operation customized =
        config.idempotencyKeyHeaderCustomizer().customize(new Operation(), handlerMethod);

    assertThat(customized.getParameters()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldDocumentApiErrorEnumCodes() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    HandlerMethod handlerMethod =
        new HandlerMethod(new DummyController(), DummyController.class.getMethod("withErrors"));

    Operation customized =
        config.apiErrorEnumCustomizer().customize(new Operation(), handlerMethod);

    var response = customized.getResponses().get("400");
    assertThat(response).isNotNull();
    assertThat(response.getDescription()).contains("INVALID_REQUEST_PAYLOAD");

    List<Map<String, String>> errorCodes =
        (List<Map<String, String>>) response.getExtensions().get("x-error-codes");
    assertThat(errorCodes)
        .extracting(map -> map.get("code"))
        .contains("INVALID_REQUEST_PAYLOAD", "MISSING_PAYLOAD_HANDLER");
  }

  private static final class DummyController {
    @PostMapping("/idempotent")
    public void idempotent() {}

    @PostMapping("/not-idempotent")
    @NotIdempotent
    public void notIdempotent() {}

    @PostMapping("/with-errors")
    @ApiErrorEnum({RequestSubmissionErrorCode.class})
    public void withErrors() {}
  }
}
