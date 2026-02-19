package com.gurch.sandbox.requesttypes;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.requests.RequestDtos;
import com.gurch.sandbox.requests.internal.RequestRepository;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WithMockUser(authorities = {"request.write", "admin.security.manage"})
class RequestTypeModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private RequestRepository requestRepository;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();
  }

  @Test
  void shouldSearchRequestTypes() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    createType("mortgage", "Mortgage", "noop", "requestTypeV2Process");

    mockMvc
        .perform(get("/api/internal/request-types/search").param("typeKeyContains", "loa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestTypes[0].typeKey").value("loan"))
        .andExpect(jsonPath("$.requestTypes[0].activeVersion").value(1));
  }

  @Test
  void shouldDeleteUnusedRequestTypeAndRejectDeleteWhenInUse() throws Exception {
    createType("unused", "Unused", "noop", "requestTypeV2Process");
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    createRequest("loan", Map.of("amount", 10));

    mockMvc
        .perform(
            delete("/api/internal/request-types/{typeKey}", "unused")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/internal/request-types/search").param("typeKeyContains", "unused"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestTypes.length()").value(0));

    mockMvc
        .perform(
            delete("/api/internal/request-types/{typeKey}", "loan")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("REQUEST_TYPE_IN_USE"));
  }

  @Test
  void shouldRejectCreateTypeWithInvalidProcessDefinitionKey() throws Exception {
    mockMvc
        .perform(
            post("/api/internal/request-types")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.CreateTypeRequest(
                            "bad", "Bad", "desc", "noop", "missing-process-definition-key"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROCESS_DEFINITION_KEY"));
  }

  @Test
  void shouldRejectCreateTypeWithInvalidPayloadHandlerId() throws Exception {
    mockMvc
        .perform(
            post("/api/internal/request-types")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.CreateTypeRequest(
                            "bad", "Bad", "desc", "unknown-handler", "requestTypeV1Process"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PAYLOAD_HANDLER_ID"));
  }

  @Test
  void shouldRejectUpdateTypeWithInvalidProcessDefinitionKey() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    mockMvc
        .perform(
            put("/api/internal/request-types/{typeKey}", "loan")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.ChangeTypeRequest(
                            "Loan", "desc", "amount-positive", "missing-process-definition-key"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROCESS_DEFINITION_KEY"));
  }

  private void createType(
      String typeKey, String name, String payloadHandlerId, String processDefinitionKey)
      throws Exception {
    mockMvc
        .perform(
            post("/api/internal/request-types")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.CreateTypeRequest(
                            typeKey, name, "desc", payloadHandlerId, processDefinitionKey))))
        .andExpect(status().isCreated());
  }

  private Long createRequest(String typeKey, Map<String, Object> payload) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RequestDtos.CreateRequest(
                                typeKey, objectMapper.valueToTree(payload)))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
        .getId();
  }
}
