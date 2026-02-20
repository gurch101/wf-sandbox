package com.gurch.sandbox.auth.internal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.bootstrap")
@Data
public class BootstrapAdminProperties {
  private boolean enabled;
  private String username;
  private String email;
  private String password;
}
