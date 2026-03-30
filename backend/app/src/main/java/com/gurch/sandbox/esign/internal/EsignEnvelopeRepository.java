package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface EsignEnvelopeRepository extends ListCrudRepository<EsignEnvelopeEntity, Long> {
  Optional<EsignEnvelopeEntity> findByExternalEnvelopeId(String externalEnvelopeId);

  List<EsignEnvelopeEntity> findByStatusInAndExternalEnvelopeIdIsNotNull(
      Collection<EsignEnvelopeStatus> statuses);
}
