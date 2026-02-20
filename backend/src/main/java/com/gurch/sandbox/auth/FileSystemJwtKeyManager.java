package com.gurch.sandbox.auth;

import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Loads, generates, and persists RSA signing keys for JWT issuance. */
@Component
public class FileSystemJwtKeyManager {

  private final JwtKeyProperties properties;

  /** Creates a key manager backed by configured filesystem key paths. */
  public FileSystemJwtKeyManager(JwtKeyProperties properties) {
    this.properties = properties;
  }

  /** Loads active and retired signing keys, generating the active key when configured. */
  public List<RSAKey> loadSigningKeys() {
    try {
      Path directory = Paths.get(properties.getDirectory());
      Files.createDirectories(directory);

      List<RSAKey> keys = new ArrayList<>();
      keys.add(
          loadOrGenerate(directory, properties.getActiveKeyId(), properties.isGenerateIfMissing()));

      Set<String> retiredKeyIds = new LinkedHashSet<>(properties.getRetiredKeyIds());
      retiredKeyIds.removeIf(value -> value == null || value.isBlank());
      retiredKeyIds.remove(properties.getActiveKeyId());
      for (String retiredKeyId : retiredKeyIds) {
        keys.add(loadOrGenerate(directory, retiredKeyId, false));
      }
      return keys;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to initialize JWT key directory", e);
    }
  }

  private RSAKey loadOrGenerate(Path directory, String keyId, boolean generateIfMissing) {
    try {
      Path privateKeyPath = directory.resolve(keyId + ".private.pem");
      Path publicKeyPath = directory.resolve(keyId + ".public.pem");
      if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
        return toRsaKey(keyId, readPublicKey(publicKeyPath), readPrivateKey(privateKeyPath));
      }
      if (!generateIfMissing) {
        throw new IllegalStateException("Missing key material for key id: " + keyId);
      }
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();
      writePem(privateKeyPath, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
      writePem(publicKeyPath, "PUBLIC KEY", keyPair.getPublic().getEncoded());
      return toRsaKey(
          keyId, (RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
    } catch (Exception e) {
      throw new IllegalStateException("Unable to load signing key: " + keyId, e);
    }
  }

  private RSAPublicKey readPublicKey(Path publicKeyPath) throws Exception {
    byte[] bytes = readPem(publicKeyPath);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PublicKey key = keyFactory.generatePublic(new X509EncodedKeySpec(bytes));
    return (RSAPublicKey) key;
  }

  private RSAPrivateKey readPrivateKey(Path privateKeyPath) throws Exception {
    byte[] bytes = readPem(privateKeyPath);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PrivateKey key = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    return (RSAPrivateKey) key;
  }

  private void writePem(Path path, String type, byte[] encoded) throws IOException {
    Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
    String body = encoder.encodeToString(encoded);
    String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    Files.writeString(path, pem, StandardCharsets.US_ASCII);
  }

  private byte[] readPem(Path path) throws IOException {
    String content = Files.readString(path, StandardCharsets.US_ASCII);
    String normalized =
        content
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(normalized);
  }

  private RSAKey toRsaKey(String keyId, RSAPublicKey publicKey, RSAPrivateKey privateKey) {
    return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
  }
}
