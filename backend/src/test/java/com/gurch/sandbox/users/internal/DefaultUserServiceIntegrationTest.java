package com.gurch.sandbox.users.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.users.UserApi;
import com.gurch.sandbox.users.UserCommand;
import com.gurch.sandbox.web.ValidationErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DefaultUserServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private UserApi userApi;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository.findAll().stream()
        .filter(user -> user.getId() > 1)
        .forEach(userRepository::delete);
  }

  @Test
  void shouldRejectDuplicateUsername() {
    userApi.create(
        UserCommand.builder()
            .username("duplicate-user")
            .email("first@example.com")
            .active(true)
            .build());

    assertThatThrownBy(
            () ->
                userApi.create(
                    UserCommand.builder()
                        .username("duplicate-user")
                        .email("second@example.com")
                        .active(true)
                        .build()))
        .isInstanceOf(ValidationErrorException.class)
        .satisfies(
            error ->
                assertThat(((ValidationErrorException) error).getErrors().getFirst().code())
                    .isEqualTo("USERNAME_ALREADY_EXISTS"));
  }

  @Test
  void shouldRejectDuplicateEmail() {
    userApi.create(
        UserCommand.builder()
            .username("first-user")
            .email("duplicate@example.com")
            .active(true)
            .build());

    assertThatThrownBy(
            () ->
                userApi.create(
                    UserCommand.builder()
                        .username("second-user")
                        .email("duplicate@example.com")
                        .active(true)
                        .build()))
        .isInstanceOf(ValidationErrorException.class)
        .satisfies(
            error ->
                assertThat(((ValidationErrorException) error).getErrors().getFirst().code())
                    .isEqualTo("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void shouldRejectMissingTenantReference() {
    assertThatThrownBy(
            () ->
                userApi.create(
                    UserCommand.builder()
                        .username("tenant-bound-user")
                        .email("tenant-bound-user@example.com")
                        .active(true)
                        .tenantId(Integer.MAX_VALUE)
                        .build()))
        .isInstanceOf(ValidationErrorException.class)
        .satisfies(
            error ->
                assertThat(((ValidationErrorException) error).getErrors().getFirst().code())
                    .isEqualTo("TENANT_NOT_FOUND"));
  }
}
