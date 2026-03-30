package com.gurch.sandbox.esign.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.docusign.esign.model.RecipientViewRequest;
import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignSignerDeliveryMethod;
import org.junit.jupiter.api.Test;

class DocuSignSdkGatewayTest {

  @Test
  void shouldUseHostIdentityForInPersonRecipientViewRequests() {
    DocuSignProperties properties = new DocuSignProperties();
    properties.setHostEmail("host@example.com");
    properties.setHostName("Signing Host");
    properties.setReturnUrl("https://app.local/docusign/return");
    DocuSignSdkGateway gateway = new DocuSignSdkGateway(properties, null);

    RecipientViewRequest request =
        gateway.toRecipientViewRequest(
            new SignerRequest(
                "s1",
                "Pat Doe",
                null,
                "1",
                EsignDeliveryMode.IN_PERSON,
                null,
                EsignAuthMethod.NONE,
                null,
                null,
                "/s1/",
                "/d1/",
                1));

    assertThat(request.getEmail()).isEqualTo("host@example.com");
    assertThat(request.getUserName()).isEqualTo("Signing Host");
    assertThat(request.getClientUserId()).isNull();
    assertThat(request.getRecipientId()).isEqualTo("1");
    assertThat(request.getReturnUrl()).isEqualTo("https://app.local/docusign/return");
  }

  @Test
  void shouldUseSignerIdentityForRemoteRecipientViewRequests() {
    DocuSignProperties properties = new DocuSignProperties();
    properties.setHostEmail("host@example.com");
    properties.setHostName("Signing Host");
    properties.setReturnUrl("https://app.local/docusign/return");
    DocuSignSdkGateway gateway = new DocuSignSdkGateway(properties, null);

    RecipientViewRequest request =
        gateway.toRecipientViewRequest(
            new SignerRequest(
                "s1",
                "Pat Doe",
                "pat@example.com",
                "1",
                EsignDeliveryMode.REMOTE,
                EsignSignerDeliveryMethod.EMAIL,
                EsignAuthMethod.PASSCODE,
                "1234",
                null,
                "/s1/",
                "/d1/",
                1));

    assertThat(request.getEmail()).isEqualTo("pat@example.com");
    assertThat(request.getUserName()).isEqualTo("Pat Doe");
    assertThat(request.getClientUserId()).isEqualTo("s1");
    assertThat(request.getRecipientId()).isEqualTo("1");
    assertThat(request.getReturnUrl()).isEqualTo("https://app.local/docusign/return");
  }

  @Test
  void shouldAppendLocaleToEmbeddedSigningUrl() {
    DocuSignProperties properties = new DocuSignProperties();
    DocuSignSdkGateway gateway = new DocuSignSdkGateway(properties, null);

    assertThat(gateway.withLocale("https://example.test/sign", "fr-CA"))
        .isEqualTo("https://example.test/sign?locale=fr_CA");
    assertThat(gateway.withLocale("https://example.test/sign?foo=bar", "fr"))
        .isEqualTo("https://example.test/sign?foo=bar&locale=fr");
  }
}
