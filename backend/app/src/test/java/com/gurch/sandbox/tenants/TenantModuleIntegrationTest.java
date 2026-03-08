package com.gurch.sandbox.tenants;

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
import com.gurch.sandbox.documenttemplates.internal.DocumentTemplateRepository;
import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.tenants.internal.TenantEntity;
import com.gurch.sandbox.tenants.internal.TenantRepository;
import com.gurch.sandbox.users.internal.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class TenantModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository.findAll().stream()
        .filter(user -> user.getId() > 1)
        .forEach(userRepository::delete);
    documentTemplateRepository.deleteAll();
    tenantRepository.deleteAll();
  }

  @Test
  void shouldCreateGetUpdateAndDeleteTenant() throws Exception {
    Integer tenantId = createTenant("acme", true);

    mockMvc
        .perform(get("/api/admin/tenants/{id}", tenantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(tenantId))
        .andExpect(jsonPath("$.name").value("acme"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(0));

    mockMvc
        .perform(
            put("/api/admin/tenants/{id}", tenantId)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TenantDtos.UpdateTenantRequest("acme-updated", false, 0L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(tenantId));

    mockMvc
        .perform(get("/api/admin/tenants/{id}", tenantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("acme-updated"))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(
            delete("/api/admin/tenants/{id}", tenantId)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/admin/tenants/{id}", tenantId)).andExpect(status().isNotFound());

    assertThat(auditActionsFor("tenants", tenantId.toString()))
        .containsExactly("DELETE", "UPDATE", "CREATE");
  }

  @Test
  void shouldSearchTenantsByName() throws Exception {
    createTenant("acme", true);
    createTenant("globex", true);

    mockMvc
        .perform(get("/api/admin/tenants/search").param("nameContains", "acm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("acme"));
  }

  private Integer createTenant(String name, boolean active) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/admin/tenants")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new TenantDtos.CreateTenantRequest(name, active))))
            .andExpect(status().isCreated())
            .andReturn();

    Integer tenantId =
        objectMapper
            .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
            .getId()
            .intValue();

    TenantEntity created = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(created.getName()).isEqualTo(name);
    assertThat(created.isActive()).isEqualTo(active);
    return tenantId;
  }
}
