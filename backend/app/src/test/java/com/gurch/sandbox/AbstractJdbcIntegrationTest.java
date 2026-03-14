package com.gurch.sandbox;

import com.gurch.sandbox.audit.internal.AuditLogEventEntity;
import com.gurch.sandbox.audit.internal.AuditLogEventRepository;
import com.gurch.sandbox.security.CurrentUserProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "1")
public abstract class AbstractJdbcIntegrationTest {
  // Shared Spring Boot + Testcontainers context for integration tests.

  @Autowired protected AuditLogEventRepository auditLogEventRepository;
  @MockitoBean protected CurrentUserProvider currentUserProvider;

  @BeforeEach
  void initDefaultCurrentUserContext() {
    Mockito.when(currentUserProvider.currentUserId()).thenReturn(Optional.of(1));
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
  }

  protected List<String> auditActionsFor(String resourceType, String resourceId) {
    return StreamSupport.stream(auditLogEventRepository.findAll().spliterator(), false)
        .filter(event -> resourceType.equals(event.getResourceType()))
        .filter(event -> resourceId.equals(event.getResourceId()))
        .sorted(
            Comparator.comparing(AuditLogEventEntity::getCreatedAt)
                .thenComparing(AuditLogEventEntity::getId)
                .reversed())
        .map(event -> event.getAction().name())
        .toList();
  }
}
