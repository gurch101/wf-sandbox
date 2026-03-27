package com.gurch.sandbox.esign.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
@RequiredArgsConstructor
public class DocuSignWebhookVerifier {

  public static final String SIGNATURE_HEADER_NAME = "X-DocuSign-Signature-1";

  private static final String HMAC_SHA_256 = "HmacSHA256";

  private final DocuSignProperties properties;

  public void verify(String signature, byte[] rawPayload) {
    String secret = StringUtils.trimToNull(properties.getWebhookHmacSecret());
    if (secret == null) {
      throw new DocuSignWebhookConfigurationException(
          "DocuSign webhook HMAC secret is not configured");
    }
    String providedSignature = StringUtils.trimToNull(signature);
    if (providedSignature == null) {
      throw new DocuSignWebhookAuthenticationException("Missing DocuSign webhook signature");
    }

    String computedSignature = computeSignature(secret, rawPayload == null ? new byte[0] : rawPayload);
    if (!MessageDigest.isEqual(
        computedSignature.getBytes(StandardCharsets.UTF_8),
        providedSignature.getBytes(StandardCharsets.UTF_8))) {
      throw new DocuSignWebhookAuthenticationException("Invalid DocuSign webhook signature");
    }
  }

  private static String computeSignature(String secret, byte[] rawPayload) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
      byte[] digest = mac.doFinal(rawPayload);
      return Base64.getEncoder().encodeToString(digest);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not compute DocuSign webhook HMAC", e);
    }
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  static class DocuSignWebhookAuthenticationException extends RuntimeException {
    DocuSignWebhookAuthenticationException(String message) {
      super(message);
    }
  }

  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  static class DocuSignWebhookConfigurationException extends RuntimeException {
    DocuSignWebhookConfigurationException(String message) {
      super(message);
    }
  }
}
