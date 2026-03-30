package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.internal.models.EsignSignerEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface EsignSignerRepository extends ListCrudRepository<EsignSignerEntity, Long> {
  List<EsignSignerEntity> findByEnvelopeId(Long envelopeId);

  List<EsignSignerEntity> findByEnvelopeIdIn(Collection<Long> envelopeIds);
}
