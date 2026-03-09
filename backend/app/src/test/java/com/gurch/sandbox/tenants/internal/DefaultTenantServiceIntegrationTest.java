package com.gurch.sandbox.tenants.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.tenants.TenantApi;
import com.gurch.sandbox.tenants.TenantCommand;
import com.gurch.sandbox.users.UserApi;
import com.gurch.sandbox.users.UserCommand;
import com.gurch.sandbox.users.internal.UserRepository;
import com.gurch.sandbox.web.ValidationErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DefaultTenantServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private TenantApi tenantApi;
  @Autowired private UserApi userApi;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM document_templates");
    userRepository.findAll().stream()
        .filter(user -> user.getId() > 1)
        .forEach(userRepository::delete);
    tenantRepository.deleteAll();
  }

  @Test
  void shouldRejectDuplicateTenantName() {
    tenantApi.create(TenantCommand.builder().name("duplicate").active(true).build());

    assertThatThrownBy(
            () -> tenantApi.create(TenantCommand.builder().name("duplicate").active(true).build()))
        .isInstanceOf(ValidationErrorException.class)
        .satisfies(
            error ->
                assertThat(((ValidationErrorException) error).getErrors().getFirst().code())
                    .isEqualTo("TENANT_NAME_ALREADY_EXISTS"));
  }

  @Test
  void shouldRejectDeleteWhenTenantInUseByUser() {
    Integer tenantId =
        tenantApi.create(TenantCommand.builder().name("in-use").active(true).build());
    userApi.create(
        UserCommand.builder()
            .username("tenant-user")
            .email("tenant-user@example.com")
            .active(true)
            .tenantId(tenantId)
            .build());

    assertThatThrownBy(() -> tenantApi.deleteById(tenantId))
        .isInstanceOf(ValidationErrorException.class)
        .satisfies(
            error ->
                assertThat(((ValidationErrorException) error).getErrors().getFirst().code())
                    .isEqualTo("TENANT_IN_USE"));
  }
}
