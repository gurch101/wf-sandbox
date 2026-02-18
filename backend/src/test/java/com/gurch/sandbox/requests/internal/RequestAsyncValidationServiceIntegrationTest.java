package com.gurch.sandbox.requests.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.requests.CreateRequestCommand;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import com.gurch.sandbox.web.NotFoundException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RequestAsyncValidationServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private RequestApi requestApi;
  @Autowired private RequestTypeApi requestTypeApi;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestAsyncValidationService requestAsyncValidationService;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();

    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan")
            .description("desc")
            .payloadHandlerId("amount-positive")
            .processDefinitionKey("requestTypeV1Process")
            .build());
  }

  @Test
  void shouldResolveAsyncValidationThroughRegistry() {
    Long requestId =
        requestApi
            .createAndSubmit(
                CreateRequestCommand.builder()
                    .requestTypeKey("loan")
                    .payload(objectMapper.valueToTree(Map.of("amount", 25)))
                    .build())
            .getId();

    Map<String, Object> variables = requestAsyncValidationService.validate(requestId);
    assertThat(variables).containsEntry("asyncValidationPassed", true);
  }

  @Test
  void shouldFailWhenRequestDoesNotExist() {
    assertThatThrownBy(() -> requestAsyncValidationService.validate(999_999L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Request not found");
  }
}
