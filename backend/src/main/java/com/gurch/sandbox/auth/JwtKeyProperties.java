package com.gurch.sandbox.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties controlling JWT RSA key storage and rotation. */
@ConfigurationProperties("auth.jwt.keys")
public class JwtKeyProperties {

  private String directory = System.getProperty("java.io.tmpdir") + "/sandbox-jwt-keys";
  private String activeKeyId = "sandbox-rsa-key-v1";
  private List<String> retiredKeyIds = new ArrayList<>();
  private boolean generateIfMissing = true;

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public String getActiveKeyId() {
    return activeKeyId;
  }

  public void setActiveKeyId(String activeKeyId) {
    this.activeKeyId = activeKeyId;
  }

  public List<String> getRetiredKeyIds() {
    return List.copyOf(retiredKeyIds);
  }

  public void setRetiredKeyIds(List<String> retiredKeyIds) {
    this.retiredKeyIds = retiredKeyIds == null ? new ArrayList<>() : new ArrayList<>(retiredKeyIds);
  }

  public boolean isGenerateIfMissing() {
    return generateIfMissing;
  }

  public void setGenerateIfMissing(boolean generateIfMissing) {
    this.generateIfMissing = generateIfMissing;
  }
}
