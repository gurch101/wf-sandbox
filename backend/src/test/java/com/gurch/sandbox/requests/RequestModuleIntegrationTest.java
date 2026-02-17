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
import com.gurch.sandbox.requests.internal.RequestDtos;
import com.gurch.sandbox.requests.internal.RequestEntity;
import com.gurch.sandbox.requests.internal.RequestRepository;
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

  @BeforeEach
  void setUp() {
    repository.deleteAll();
  }

  @Test
  void shouldPerformCrudOperations() throws Exception {
    // Create
    RequestDtos.CreateRequest createRequest =
        new RequestDtos.CreateRequest("Test Request", RequestStatus.DRAFT);
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").doesNotExist())
            .andReturn();

    CreateResponse createResponse =
        objectMapper.readValue(
            createResult.getResponse().getContentAsString(), CreateResponse.class);
    Long id = createResponse.getId();

    // Get by ID
    mockMvc
        .perform(get("/api/requests/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Request"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());

    // Update
    RequestDtos.UpdateRequest updateRequest =
        new RequestDtos.UpdateRequest("Updated Request", RequestStatus.IN_PROGRESS, 0L);
    mockMvc
        .perform(
            put("/api/requests/{id}", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Request"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.updatedAt").exists())
        .andExpect(jsonPath("$.version").value(1L));

    // Conflict (using stale version 0L instead of new version 1L)
    RequestDtos.UpdateRequest staleUpdateRequest =
        new RequestDtos.UpdateRequest("Stale Update", RequestStatus.IN_PROGRESS, 0L);
    mockMvc
        .perform(
            put("/api/requests/{id}", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(staleUpdateRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Optimistic Locking Failure"))
        .andExpect(jsonPath("$.status").value(409));

    // Delete
    mockMvc
        .perform(
            delete("/api/requests/{id}", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/requests/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Request not found"));
  }

  @Test
  void shouldReturnBadRequestForInvalidCreateRequest() throws Exception {
    String invalidJson = "{\"name\":\"\", \"status\":\"INVALID_STATUS\"}";

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors[0].name").value("status"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.errors[0].message").exists());
  }

  @Test
  void shouldUseValidationMessageForEmptyEnumValue() throws Exception {
    String invalidJson = "{\"name\":\"Valid Name\", \"status\":\"\"}";

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors[0].name").value("status"))
        .andExpect(jsonPath("$.errors[0].code").value("NotNull"))
        .andExpect(jsonPath("$.errors[0].message").value("status is required"));
  }

  @Test
  void shouldReturnBadRequestWhenMissingIdempotencyKey() throws Exception {
    RequestDtos.CreateRequest createRequest =
        new RequestDtos.CreateRequest("Test Request", RequestStatus.DRAFT);
    mockMvc
        .perform(
            post("/api/requests")
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

    // Search by name
    mockMvc
        .perform(get("/api/requests/search").param("nameContains", "an"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(1))
        .andExpect(jsonPath("$.requests[0].name").value("Banana"));

    // Search by status
    mockMvc
        .perform(
            get("/api/requests/search").param("statuses", "DRAFT").param("statuses", "COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));

    // Search by ID
    List<RequestEntity> all = repository.findAll();
    Long id1 = all.get(0).getId();
    Long id2 = all.get(1).getId();

    mockMvc
        .perform(
            get("/api/requests/search").param("ids", id1.toString()).param("ids", id2.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));

    // Search with pagination
    mockMvc
        .perform(get("/api/requests/search").param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));

    mockMvc
        .perform(get("/api/requests/search").param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(1));
  }
}
