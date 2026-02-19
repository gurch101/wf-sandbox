package com.gurch.sandbox.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gurch.sandbox.auth.FileSystemJwtKeyManager;
import com.gurch.sandbox.auth.JwtKeyProperties;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemJwtKeyManagerTest {

  @TempDir Path tempDir;

  @Test
  void shouldPersistAndReuseActiveKeyAcrossLoads() throws Exception {
    JwtKeyProperties properties = new JwtKeyProperties();
    properties.setDirectory(tempDir.toString());
    properties.setActiveKeyId("test-active-key");

    FileSystemJwtKeyManager keyManager = new FileSystemJwtKeyManager(properties);
    List<RSAKey> firstLoad = keyManager.loadSigningKeys();
    List<RSAKey> secondLoad = keyManager.loadSigningKeys();

    assertThat(firstLoad).hasSize(1);
    assertThat(secondLoad).hasSize(1);
    assertThat(secondLoad.get(0).getKeyID()).isEqualTo("test-active-key");
    assertThat(secondLoad.get(0).toRSAPublicKey().getModulus())
        .isEqualTo(firstLoad.get(0).toRSAPublicKey().getModulus());
  }

  @Test
  void shouldLoadRetiredKeysAfterActiveKey() {
    JwtKeyProperties bootstrapProperties = new JwtKeyProperties();
    bootstrapProperties.setDirectory(tempDir.toString());
    bootstrapProperties.setActiveKeyId("key-v1");
    bootstrapProperties.setRetiredKeyIds(List.of());
    new FileSystemJwtKeyManager(bootstrapProperties).loadSigningKeys();

    JwtKeyProperties properties = new JwtKeyProperties();
    properties.setDirectory(tempDir.toString());
    properties.setActiveKeyId("key-v2");
    properties.setRetiredKeyIds(List.of());

    FileSystemJwtKeyManager initialManager = new FileSystemJwtKeyManager(properties);
    initialManager.loadSigningKeys();

    JwtKeyProperties verifyProperties = new JwtKeyProperties();
    verifyProperties.setDirectory(tempDir.toString());
    verifyProperties.setActiveKeyId("key-v2");
    verifyProperties.setRetiredKeyIds(List.of("key-v1"));
    verifyProperties.setGenerateIfMissing(false);

    FileSystemJwtKeyManager verifyManager = new FileSystemJwtKeyManager(verifyProperties);
    List<RSAKey> keys = verifyManager.loadSigningKeys();
    assertThat(keys).hasSize(2);
    assertThat(keys.get(0).getKeyID()).isEqualTo("key-v2");
    assertThat(keys.get(1).getKeyID()).isEqualTo("key-v1");
  }

  @Test
  void shouldFailWhenRetiredKeyIsMissingAndGenerationDisabled() {
    JwtKeyProperties properties = new JwtKeyProperties();
    properties.setDirectory(tempDir.toString());
    properties.setActiveKeyId("active");
    properties.setRetiredKeyIds(List.of("missing-retired"));
    properties.setGenerateIfMissing(false);

    FileSystemJwtKeyManager keyManager = new FileSystemJwtKeyManager(properties);
    assertThatThrownBy(keyManager::loadSigningKeys).isInstanceOf(IllegalStateException.class);
  }
}
