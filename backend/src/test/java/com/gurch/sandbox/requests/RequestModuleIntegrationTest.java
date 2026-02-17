package com.gurch.sandbox.requests;

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
import com.gurch.sandbox.requests.internal.RequestEntity;
import com.gurch.sandbox.requests.internal.RequestRepository;
import com.gurch.sandbox.requests.internal.RequestTaskEntity;
import com.gurch.sandbox.requests.internal.RequestTaskRepository;
import com.gurch.sandbox.requests.internal.RequestTaskStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@WithMockUser
class RequestModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RequestRepository repository;
  @Autowired private RequestTaskRepository requestTaskRepository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void shouldPerformCrudOperations() throws Exception {
    RequestDtos.CreateDraftRequest createRequest =
        new RequestDtos.CreateDraftRequest("Test Request");
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests/drafts")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

    CreateResponse createResponse =
        objectMapper.readValue(
            createResult.getResponse().getContentAsString(), CreateResponse.class);
    Long id = createResponse.getId();

    mockMvc
        .perform(get("/api/requests/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Request"))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    RequestDtos.UpdateDraftRequest updateRequest =
        new RequestDtos.UpdateDraftRequest("Updated Request", 0L);
    mockMvc
        .perform(
            put("/api/requests/{id}", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Request"))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.version").value(1L));

    mockMvc
        .perform(
            post("/api/requests/{id}/submit", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    mockMvc
        .perform(
            delete("/api/requests/{id}", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/requests/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Request not found"));
  }

  @Test
  void shouldReturnBadRequestForInvalidCreateDraftRequest() throws Exception {
    String invalidJson = "{\"name\":\"\"}";

    mockMvc
        .perform(
            post("/api/requests/drafts")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors[0].name").value("name"));
  }

  @Test
  void shouldReturnBadRequestWhenUpdatingNonDraft() throws Exception {
    RequestDtos.SubmitRequest submitRequest = new RequestDtos.SubmitRequest("Submitted");
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests/submit")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(submitRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    Long id =
        objectMapper
            .readValue(createResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();

    RequestDtos.UpdateDraftRequest updateRequest =
        new RequestDtos.UpdateDraftRequest("Should Fail", 0L);
    mockMvc
        .perform(
            put("/api/requests/{id}", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_DRAFT_UPDATE_STATUS"));
  }

  @Test
  void shouldReturnBadRequestWhenSubmittingNonDraft() throws Exception {
    RequestDtos.SubmitRequest submitRequest = new RequestDtos.SubmitRequest("Submitted");
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests/submit")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(submitRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    Long id =
        objectMapper
            .readValue(createResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();

    mockMvc
        .perform(
            post("/api/requests/{id}/submit", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_DRAFT_SUBMIT_STATUS"));
  }

  @Test
  void shouldSubmitDraftWithAtomicUpdatePayload() throws Exception {
    RequestDtos.CreateDraftRequest createRequest = new RequestDtos.CreateDraftRequest("Draft Name");
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests/drafts")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    Long id =
        objectMapper
            .readValue(createResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();

    RequestDtos.UpdateDraftRequest submitWithUpdate =
        new RequestDtos.UpdateDraftRequest("Updated Before Submit", 0L);
    mockMvc
        .perform(
            post("/api/requests/{id}/submit", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitWithUpdate)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Before Submit"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.version").value(2L));
  }

  @Test
  void shouldReturnBadRequestWhenMissingIdempotencyKey() throws Exception {
    RequestDtos.CreateDraftRequest createRequest = new RequestDtos.CreateDraftRequest("Test");
    mockMvc
        .perform(
            post("/api/requests/drafts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Missing Idempotency Key"));
  }

  @Test
  void shouldSearchRequests() throws Exception {
    repository.save(RequestEntity.builder().name("Apple").status(RequestStatus.DRAFT).build());
    repository.save(
        RequestEntity.builder().name("Banana").status(RequestStatus.IN_PROGRESS).build());
    repository.save(RequestEntity.builder().name("Cherry").status(RequestStatus.COMPLETED).build());

    mockMvc
        .perform(get("/api/requests/search").param("nameContains", "an"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(1))
        .andExpect(jsonPath("$.requests[0].name").value("Banana"));

    mockMvc
        .perform(
            get("/api/requests/search").param("statuses", "DRAFT").param("statuses", "COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));

    List<RequestEntity> all = repository.findAll();
    Long id1 = all.get(0).getId();
    Long id2 = all.get(1).getId();

    mockMvc
        .perform(
            get("/api/requests/search").param("ids", id1.toString()).param("ids", id2.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));

    mockMvc
        .perform(get("/api/requests/search").param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));

    mockMvc
        .perform(get("/api/requests/search").param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(1));

    RequestDtos.SubmitRequest submittedRequest =
        new RequestDtos.SubmitRequest("Search By Assignee");
    mockMvc
        .perform(
            post("/api/requests/submit")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submittedRequest)))
        .andExpect(status().isCreated());

    RequestEntity assignedRequest =
        repository.save(
            RequestEntity.builder()
                .name("Search By Assignee")
                .status(RequestStatus.IN_PROGRESS)
                .build());
    requestTaskRepository.save(
        RequestTaskEntity.builder()
            .requestId(assignedRequest.getId())
            .processInstanceId("pi-web-search")
            .taskId("task-web-search")
            .name("Assigned Task")
            .status(RequestTaskStatus.ACTIVE)
            .assignee("demo")
            .build());

    mockMvc
        .perform(get("/api/requests/search").param("taskAssignee", "demo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(1))
        .andExpect(jsonPath("$.requests[0].name").value("Search By Assignee"));

    mockMvc
        .perform(
            get("/api/requests/search")
                .param("taskAssignees", "missing-user")
                .param("taskAssignees", "demo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(1))
        .andExpect(jsonPath("$.requests[0].name").value("Search By Assignee"));
  }
}
