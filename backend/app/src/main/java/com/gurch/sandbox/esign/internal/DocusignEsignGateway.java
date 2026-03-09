package com.gurch.sandbox.esign.internal;

public interface DocusignEsignGateway {
  DocusignEnvelopeResult createEnvelope(
      TenantDocusignConfigEntity config, DocusignEnvelopeRequest request);

  void voidEnvelope(TenantDocusignConfigEntity config, String envelopeId, String reason);

  void resendEnvelope(TenantDocusignConfigEntity config, String envelopeId);

  DocusignEnvelopeArtifacts downloadArtifacts(TenantDocusignConfigEntity config, String envelopeId);
}
