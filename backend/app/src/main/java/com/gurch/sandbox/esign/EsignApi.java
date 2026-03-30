package com.gurch.sandbox.esign;

import com.gurch.sandbox.esign.dto.EsignCreateEnvelopeCommand;
import com.gurch.sandbox.esign.dto.EsignDocumentDownload;
import com.gurch.sandbox.esign.dto.EsignEmbeddedViewResponse;
import com.gurch.sandbox.esign.dto.EsignEnvelopeResponse;
import java.util.Optional;

/** Public API for creating and managing e-sign envelopes. */
public interface EsignApi {

  /** Uploads a source PDF and creates a provider envelope from parsed anchors plus signer input. */
  EsignEnvelopeResponse createEnvelope(EsignCreateEnvelopeCommand command);

  /** Returns the current envelope state when present. */
  Optional<EsignEnvelopeResponse> findById(Long id);

  /** Voids an active envelope and updates local signer/envelope state. */
  EsignEnvelopeResponse voidEnvelope(Long id, String reason);

  /** Resends notification emails for all actionable remote signers in the envelope. */
  EsignEnvelopeResponse resendEnvelope(Long id);

  /** Resends the notification email for one actionable remote signer in the envelope. */
  EsignEnvelopeResponse resendSigner(Long id, String roleKey);

  /** Creates a fresh embedded signing view URL for an in-person signer. */
  EsignEmbeddedViewResponse createEmbeddedSigningView(Long id, String roleKey, String locale);

  /** Downloads the signing certificate when available. */
  EsignDocumentDownload downloadCertificate(Long id);

  /** Downloads the completed signed PDF when available. */
  EsignDocumentDownload downloadSignedDocument(Long id);
}
