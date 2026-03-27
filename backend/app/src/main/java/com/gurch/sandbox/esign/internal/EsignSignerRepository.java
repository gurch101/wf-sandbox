package com.gurch.sandbox.esign.internal;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface EsignSignerRepository extends ListCrudRepository<EsignSignerEntity, Long> {
  List<EsignSignerEntity> findByEnvelopeId(Long envelopeId);
}
