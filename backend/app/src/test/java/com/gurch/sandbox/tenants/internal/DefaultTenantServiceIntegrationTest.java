package com.gurch.sandbox.tenants.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.documenttemplates.internal.DocumentTemplateRepository;
import com.gurch.sandbox.tenants.TenantApi;
import com.gurch.sandbox.tenants.dto.TenantCommand;
import com.gurch.sandbox.users.UserApi;
import com.gurch.sandbox.users.dto.UserCommand;
import com.gurch.sandbox.users.internal.UserRepository;
import com.gurch.sandbox.web.ValidationErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DefaultTenantServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private TenantApi tenantApi;
  @Autowired private UserApi userApi;
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
