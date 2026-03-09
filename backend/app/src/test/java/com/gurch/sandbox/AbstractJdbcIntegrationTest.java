package com.gurch.sandbox;

import com.gurch.sandbox.security.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "1")
public abstract class AbstractJdbcIntegrationTest {
  // Shared Spring Boot + Testcontainers context for integration tests.

  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean protected CurrentUserProvider currentUserProvider;

  @BeforeEach
  void initDefaultCurrentUserContext() {
    Mockito.when(currentUserProvider.currentUserId()).thenReturn(Optional.of(1));
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
  }

  protected List<String> auditActionsFor(String resourceType, String resourceId) {
    return jdbcTemplate.queryForList(
        """
        SELECT action
        FROM audit_log_events
        WHERE resource_type = ?
          AND resource_id = ?
        ORDER BY created_at DESC, id DESC
        """,
        String.class,
        resourceType,
        resourceId);
  }
}
