package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerStatus;
import com.gurch.sandbox.esign.EsignWebhookRequest;
import java.time.Instant;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Maps DocuSign Connect 2.0 JSON SIM payloads into the app's normalized webhook model. */
@Component
public class DocuSignConnectWebhookMapper {

  public EsignWebhookRequest map(DocuSignConnectWebhookPayload payload) {
    if (payload == null
        || payload.data() == null
        || StringUtils.isBlank(payload.data().envelopeId())) {
      return null;
    }

    Instant eventTimestamp =
        payload.generatedDateTime() != null ? payload.generatedDateTime() : Instant.now();
    EsignEnvelopeStatus envelopeStatus =
        firstNonNull(
            toEnvelopeStatus(payload.event()),
            payload.data().envelopeSummary() == null
                ? null
                : toEnvelopeStatusFromProviderValue(payload.data().envelopeSummary().status()));

    List<EsignWebhookRequest.SignerStatusUpdate> signerUpdates =
        StringUtils.isBlank(payload.data().recipientId())
            ? List.of()
            : List.of(
                new EsignWebhookRequest.SignerStatusUpdate(
                    payload.data().recipientId(),
                    toSignerStatus(payload.event()),
                    isViewedEvent(payload.event()) ? eventTimestamp : null,
                    isCompletedEvent(payload.event()) ? eventTimestamp : null));

    return new EsignWebhookRequest(
        payload.data().envelopeId(),
        envelopeStatus,
        eventTimestamp,
        envelopeStatus == EsignEnvelopeStatus.COMPLETED,
        signerUpdates);
  }

  private EsignEnvelopeStatus toEnvelopeStatus(String event) {
    if (StringUtils.isBlank(event)) {
      return null;
    }
    return switch (event.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "envelope-created" -> EsignEnvelopeStatus.CREATED;
      case "envelope-sent" -> EsignEnvelopeStatus.SENT;
      case "envelope-delivered" -> EsignEnvelopeStatus.DELIVERED;
      case "envelope-completed" -> EsignEnvelopeStatus.COMPLETED;
      case "envelope-declined" -> EsignEnvelopeStatus.DECLINED;
      case "envelope-voided" -> EsignEnvelopeStatus.VOIDED;
      default -> null;
    };
  }

  private EsignEnvelopeStatus toEnvelopeStatusFromProviderValue(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "created" -> EsignEnvelopeStatus.CREATED;
      case "sent" -> EsignEnvelopeStatus.SENT;
      case "delivered" -> EsignEnvelopeStatus.DELIVERED;
      case "completed" -> EsignEnvelopeStatus.COMPLETED;
      case "declined" -> EsignEnvelopeStatus.DECLINED;
      case "voided" -> EsignEnvelopeStatus.VOIDED;
      default -> null;
    };
  }

  private EsignSignerStatus toSignerStatus(String event) {
    if (StringUtils.isBlank(event)) {
      return null;
    }
    return switch (event.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "recipient-sent" -> EsignSignerStatus.SENT;
      case "recipient-delivered" -> EsignSignerStatus.DELIVERED;
      case "recipient-completed" -> EsignSignerStatus.COMPLETED;
      case "recipient-declined" -> EsignSignerStatus.DECLINED;
      default -> null;
    };
  }

  private boolean isViewedEvent(String event) {
    return "recipient-delivered".equalsIgnoreCase(StringUtils.trimToEmpty(event));
  }

  private boolean isCompletedEvent(String event) {
    return "recipient-completed".equalsIgnoreCase(StringUtils.trimToEmpty(event));
  }

  private static <T> T firstNonNull(T left, T right) {
    return left != null ? left : right;
  }
}
