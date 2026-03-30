package com.gurch.sandbox.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.esign.EsignApi;
import com.gurch.sandbox.esign.EsignController;
import com.gurch.sandbox.esign.EsignReconciliationApi;
import com.gurch.sandbox.esign.EsignWebhookApi;
import com.gurch.sandbox.esign.internal.DocuSignConnectWebhookMapper;
import com.gurch.sandbox.esign.internal.DocuSignWebhookVerifier;
import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestController;
import com.gurch.sandbox.requests.RequestSubmissionErrorCode;
import com.gurch.sandbox.security.SystemAuthenticationScope;
import com.gurch.sandbox.web.ApiErrorEnum;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
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
    RequestController controller = new RequestController(mock(RequestApi.class));
    Method method = RequestController.class.getMethod("getById", Long.class);
    HandlerMethod handlerMethod = new HandlerMethod(controller, method);

    Operation operation = new Operation();
    Operation customized = config.apiErrorEnumCustomizer().customize(operation, handlerMethod);

    assertThat(customized.getResponses()).isNull();
  }

  @Test
  void shouldAddIdempotencyHeaderForPostMappings() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    HandlerMethod handlerMethod =
        new HandlerMethod(
            new DummyController(), DummyController.class.getDeclaredMethod("idempotent"));

    Operation customized =
        config.idempotencyKeyHeaderCustomizer().customize(new Operation(), handlerMethod);

    assertThat(customized.getParameters()).isNotEmpty();
    assertThat(customized.getParameters().getFirst().get$ref())
        .isEqualTo("#/components/parameters/IdempotencyKey");
  }

  @Test
  void shouldSkipIdempotencyHeaderForNotIdempotentMethods() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    HandlerMethod handlerMethod =
        new HandlerMethod(
            new DummyController(), DummyController.class.getDeclaredMethod("notIdempotent"));

    Operation customized =
        config.idempotencyKeyHeaderCustomizer().customize(new Operation(), handlerMethod);

    assertThat(customized.getParameters()).isNull();
  }

  @Test
  void shouldAddBasicAuthRequirementForSecuredOperations() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    HandlerMethod handlerMethod =
        new HandlerMethod(
            new DummyController(), DummyController.class.getDeclaredMethod("idempotent"));

    Operation customized = config.basicAuthCustomizer().customize(new Operation(), handlerMethod);

    assertThat(customized.getSecurity()).hasSize(1);
    assertThat(customized.getSecurity().getFirst()).containsKey("basicAuth");
  }

  @Test
  void shouldSkipBasicAuthRequirementForAnonymousWebhookOperation() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    EsignController controller =
        new EsignController(
            mock(EsignApi.class),
            mock(EsignReconciliationApi.class),
            mock(EsignWebhookApi.class),
            mock(DocuSignWebhookVerifier.class),
            mock(DocuSignConnectWebhookMapper.class),
            mock(ObjectMapper.class),
            mock(SystemAuthenticationScope.class));
    HandlerMethod handlerMethod =
        new HandlerMethod(
            controller, EsignController.class.getMethod("webhook", String.class, byte[].class));

    Operation customized = config.basicAuthCustomizer().customize(new Operation(), handlerMethod);

    assertThat(customized.getSecurity()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldDocumentApiErrorEnumCodes() throws Exception {
    OpenApiConfig config = new OpenApiConfig();
    HandlerMethod handlerMethod =
        new HandlerMethod(
            new DummyController(), DummyController.class.getDeclaredMethod("withErrors"));

    Operation customized =
        config.apiErrorEnumCustomizer().customize(new Operation(), handlerMethod);

    var response = customized.getResponses().get("400");
    assertThat(response).isNotNull();
    assertThat(response.getDescription()).contains("INVALID_REQUEST_PAYLOAD");
    var errorsSchema =
        response
            .getContent()
            .get("application/problem+json")
            .getSchema()
            .getProperties()
            .get("errors");
    assertThat(errorsSchema).isInstanceOf(ArraySchema.class);
    assertThat(((ArraySchema) errorsSchema).getItems().get$ref()).isNull();

    List<Map<String, String>> errorCodes =
        (List<Map<String, String>>) response.getExtensions().get("x-error-codes");
    assertThat(errorCodes)
        .extracting(map -> map.get("code"))
        .contains("INVALID_REQUEST_PAYLOAD", "MISSING_PAYLOAD_HANDLER");
  }

  @SuppressWarnings("UnusedMethod")
  private static final class DummyController {
    @PostMapping("/idempotent")
    void idempotent() {}

    @PostMapping("/not-idempotent")
    @NotIdempotent
    void notIdempotent() {}

    @PostMapping("/with-errors")
    @ApiErrorEnum({RequestSubmissionErrorCode.class})
    void withErrors() {}
  }
}
