package com.gurch.sandbox.auth.internal;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminInitializer implements ApplicationRunner {

  private static final String ADMIN_ROLE_CODE = "PLATFORM_ADMIN";
  private static final String SECURITY_MANAGE_PERMISSION = "admin.security.manage";

  private final BootstrapAdminProperties properties;
  private final BootstrapSecurityRepository repository;
  private final PasswordEncoder passwordEncoder;

  public BootstrapAdminInitializer(
      BootstrapAdminProperties properties,
      BootstrapSecurityRepository repository,
      PasswordEncoder passwordEncoder) {
    this.properties = properties;
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isEnabled()) {
      return;
    }
    if (!StringUtils.hasText(properties.getUsername())
        || !StringUtils.hasText(properties.getEmail())
        || !StringUtils.hasText(properties.getPassword())) {
      throw new IllegalStateException(
          "auth.bootstrap.username/email/password are required when enabled");
    }

    UUID userId =
        repository.findUserIdByUsername(properties.getUsername()).orElse(UUID.randomUUID());
    repository.insertUser(userId, properties.getUsername(), properties.getEmail());
    repository.upsertCredential(userId, passwordEncoder.encode(properties.getPassword()));

    UUID roleId = repository.insertRoleIfMissing(ADMIN_ROLE_CODE, "Platform Admin");
    UUID permissionId =
        repository.insertPermissionIfMissing(
            SECURITY_MANAGE_PERMISSION, "Manage security roles, permissions, and assignments");
    repository.linkRolePermission(roleId, permissionId);
    repository.linkUserRole(userId, roleId);
  }
}
