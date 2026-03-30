package com.gurch.sandbox.requesttypes;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.gurch.sandbox.requests.dto.RequestDtos;
import com.gurch.sandbox.requests.internal.RequestRepository;
import com.gurch.sandbox.requesttypes.dto.RequestTypeDtos;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class RequestTypeModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private RequestRepository requestRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();
  }

  @Test
  void shouldSearchRequestTypes() throws Exception {
    createType("loan", "Loan", "requestTypeV1Process");
    createType("mortgage", "Mortgage", "requestTypeV2Process");

    mockMvc
        .perform(get("/api/internal/request-types/search").param("typeKeyContains", "loa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].typeKey").value("loan"))
        .andExpect(jsonPath("$.items[0].activeVersion").value(1));
  }

  @Test
  void shouldDeleteUnusedRequestTypeAndRejectDeleteWhenInUse() throws Exception {
    createType("unused", "Unused", "requestTypeV2Process");
    createType("loan", "Loan", "requestTypeV1Process");
    createRequest("loan");

    mockMvc
        .perform(
            delete("/api/internal/request-types/{typeKey}", "unused")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/internal/request-types/search").param("typeKeyContains", "unused"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));

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
                            "bad", "Bad", "desc", "missing-process-definition-key"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROCESS_DEFINITION_KEY"));
  }

  @Test
  void shouldRejectUpdateTypeWithInvalidProcessDefinitionKey() throws Exception {
    createType("loan", "Loan", "requestTypeV1Process");

    mockMvc
        .perform(
            put("/api/internal/request-types/{typeKey}", "loan")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.ChangeTypeRequest(
                            "Loan", "desc", "missing-process-definition-key"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROCESS_DEFINITION_KEY"));
  }

  @Test
  void shouldWriteAuditEventsForCreateChangeAndDeleteRequestType() throws Exception {
    createType("audit-type", "Audit Type", "requestTypeV2Process");

    Long typeId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM request_types WHERE type_key = ?", Long.class, "audit-type");

    mockMvc
        .perform(
            put("/api/internal/request-types/{typeKey}", "audit-type")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.ChangeTypeRequest(
                            "Audit Type Updated", "desc", "requestTypeV1Process"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            delete("/api/internal/request-types/{typeKey}", "audit-type")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    assertThat(auditActionsFor("request_types", typeId.toString()))
        .containsExactly("DELETE", "UPDATE", "CREATE");
  }

  private void createType(String typeKey, String name, String processDefinitionKey)
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
                            typeKey, name, "desc", processDefinitionKey))))
        .andExpect(status().isCreated());
  }

  private Long createRequest(String typeKey) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new RequestDtos.CreateRequest(typeKey))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
        .getId();
  }
}
