package com.gurch.sandbox.esign;

import java.util.Optional;

/** Public API for creating and managing e-sign envelopes. */
public interface EsignApi {

  /** Uploads a source PDF and creates a provider envelope from parsed anchors plus signer input. */
  EsignEnvelopeResponse createEnvelope(EsignCreateEnvelopeCommand command);

  /** Returns the current envelope state when present. */
  Optional<EsignEnvelopeResponse> findById(Long id);

  /** Voids an active envelope and updates local signer/envelope state. */
  EsignEnvelopeResponse voidEnvelope(Long id, String reason);

  /** Deletes an envelope and any stored artifacts. */
  void deleteEnvelope(Long id);

  /** Creates a fresh embedded signing view URL for an in-person signer. */
  EsignEmbeddedViewResponse createEmbeddedSigningView(Long id, String roleKey);

  /** Downloads the signing certificate when available. */
  EsignCertificateDownload downloadCertificate(Long id);

  /** Downloads the completed signed PDF when available. */
  EsignSignedDocumentDownload downloadSignedDocument(Long id);

  /** Applies a provider webhook payload to the stored envelope and signer state. */
  void handleWebhook(EsignWebhookRequest request);
}
