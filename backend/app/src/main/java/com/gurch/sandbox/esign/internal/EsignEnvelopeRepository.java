package com.gurch.sandbox.esign.internal;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface EsignEnvelopeRepository extends ListCrudRepository<EsignEnvelopeEntity, Long> {
  Optional<EsignEnvelopeEntity> findByExternalEnvelopeId(String externalEnvelopeId);
}
