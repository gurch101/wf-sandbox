package com.gurch.sandbox.users;

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
import com.gurch.sandbox.users.internal.UserEntity;
import com.gurch.sandbox.users.internal.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class UserModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository.findAll().stream()
        .filter(user -> user.getId() > 1)
        .forEach(userRepository::delete);
  }

  @Test
  void shouldCreateGetUpdateAndDeleteUser() throws Exception {
    Integer userId = createUser("alice", "alice@example.com", true);

    mockMvc
        .perform(get("/api/admin/users/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId))
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.version").value(0));

    mockMvc
        .perform(
            put("/api/admin/users/{id}", userId)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UserDtos.UpdateUserRequest(
                            "alice.updated@example.com", false, null, 0L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId));

    mockMvc
        .perform(get("/api/admin/users/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.email").value("alice.updated@example.com"))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.version").value(1));

    mockMvc
        .perform(
            delete("/api/admin/users/{id}", userId)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/admin/users/{id}", userId)).andExpect(status().isNotFound());
  }

  @Test
  void shouldSearchUsersByUsername() throws Exception {
    createUser("alice", "alice@example.com", true);
    createUser("bob", "bob@example.com", true);

    mockMvc
        .perform(get("/api/admin/users/search").param("usernameContains", "ali"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].username").value("alice"));
  }

  private Integer createUser(String username, String email, boolean active) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/admin/users")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new UserDtos.CreateUserRequest(username, email, active, null))))
            .andExpect(status().isCreated())
            .andReturn();

    Integer userId =
        objectMapper
            .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
            .getId()
            .intValue();

    UserEntity created = userRepository.findById(userId).orElseThrow();
    assertThat(created.getUsername()).isEqualTo(username);
    assertThat(created.getEmail()).isEqualTo(email);
    assertThat(created.isActive()).isEqualTo(active);
    return userId;
  }
}
