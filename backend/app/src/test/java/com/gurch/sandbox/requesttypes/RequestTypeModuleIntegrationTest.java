package com.gurch.sandbox.requesttypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    createType("mortgage", "Mortgage", "noop", "requestTypeV2Process");

    mockMvc
        .perform(get("/api/internal/request-types/search").param("typeKeyContains", "loa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].typeKey").value("loan"))
        .andExpect(jsonPath("$.items[0].activeVersion").value(1));
  }

  @Test
  void shouldReturnModelerCapabilitiesForRequestTypeVersion() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    mockMvc
        .perform(
            get(
                "/api/internal/request-types/{typeKey}/versions/{version}/modeler-capabilities",
                "loan",
                1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeKey").value("loan"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.inputs.payloadFields[0].key").value("request.amount"))
        .andExpect(jsonPath("$.inputs.payloadFields[0].type").value("NUMBER"))
        .andExpect(
            jsonPath("$.inputs.workflowFields[0].key").value("workflow.lastCompletedTaskKey"))
        .andExpect(jsonPath("$.assignmentModes[0]").value("CANDIDATE_USERS"))
        .andExpect(jsonPath("$.availableEscalationHandlers[0]").value("OPS_REROUTE"));
  }

  @Test
  void shouldPublishWorkflowModelForRequestTypeVersion() throws Exception {
    createType("loan", "Loan", "amount-positive", null);

    mockMvc
        .perform(
            post(
                    "/api/internal/request-types/{typeKey}/versions/{version}/workflow-model",
                    "loan",
                    1)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.PublishWorkflowModelRequest(validLoanWorkflowXml()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeKey").value("loan"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.processDefinitionKey").value("loanApprovalProcess"));

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT wmir.process_definition_key, wmir.bpmn_element_id, wmir.reference_kind, wmir.input_key
                FROM workflow_model_input_references wmir
                JOIN request_type_versions rtv ON rtv.id = wmir.request_type_version_id
                JOIN request_types rt ON rt.id = rtv.request_type_id
                WHERE rt.type_key = ?
                  AND rtv.type_version = ?
                ORDER BY wmir.id
                """,
                "loan",
                1))
        .extracting(
            row -> row.get("process_definition_key"),
            row -> row.get("bpmn_element_id"),
            row -> row.get("reference_kind"),
            row -> row.get("input_key"))
        .containsExactly(
            tuple(
                "loanApprovalProcess",
                "flow_to_approve",
                "SEQUENCE_FLOW_CONDITION",
                "request.amount"));
  }

  @Test
  void shouldRejectWorkflowModelWithUnknownInputReference() throws Exception {
    createType("loan", "Loan", "amount-positive", null);

    mockMvc
        .perform(
            post(
                    "/api/internal/request-types/{typeKey}/versions/{version}/workflow-model",
                    "loan",
                    1)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.PublishWorkflowModelRequest(invalidLoanWorkflowXml()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_WORKFLOW_MODEL"));

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM workflow_model_input_references wmir
                JOIN request_type_versions rtv ON rtv.id = wmir.request_type_version_id
                JOIN request_types rt ON rt.id = rtv.request_type_id
                WHERE rt.type_key = ?
                  AND rtv.type_version = ?
                """,
                Integer.class,
                "loan",
                1))
        .isZero();
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

  @Test
  void shouldWriteAuditEventsForCreateChangeAndDeleteRequestType() throws Exception {
    createType("audit-type", "Audit Type", "noop", "requestTypeV2Process");

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
                            "Audit Type Updated", "desc", "noop", "requestTypeV1Process"))))
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

  private String validLoanWorkflowXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions
          xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
          xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          targetNamespace="http://gurch.com/sandbox/process">
          <bpmn:process
            id="loanApprovalProcess"
            isExecutable="true"
            camunda:historyTimeToLive="30">
            <bpmn:startEvent id="startEvent">
              <bpmn:outgoing>flow_start_to_gateway</bpmn:outgoing>
            </bpmn:startEvent>
            <bpmn:exclusiveGateway id="approvalGateway" default="flow_to_reject">
              <bpmn:incoming>flow_start_to_gateway</bpmn:incoming>
              <bpmn:outgoing>flow_to_approve</bpmn:outgoing>
              <bpmn:outgoing>flow_to_reject</bpmn:outgoing>
            </bpmn:exclusiveGateway>
            <bpmn:endEvent id="approvedEnd">
              <bpmn:incoming>flow_to_approve</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:endEvent id="rejectedEnd">
              <bpmn:incoming>flow_to_reject</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="flow_start_to_gateway" sourceRef="startEvent" targetRef="approvalGateway"/>
            <bpmn:sequenceFlow id="flow_to_approve" sourceRef="approvalGateway" targetRef="approvedEnd">
              <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">
                ${request.amount > 100}
              </bpmn:conditionExpression>
            </bpmn:sequenceFlow>
            <bpmn:sequenceFlow id="flow_to_reject" sourceRef="approvalGateway" targetRef="rejectedEnd"/>
          </bpmn:process>
        </bpmn:definitions>
        """;
  }

  private String invalidLoanWorkflowXml() {
    return validLoanWorkflowXml().replace("request.amount", "request.unknownField");
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
