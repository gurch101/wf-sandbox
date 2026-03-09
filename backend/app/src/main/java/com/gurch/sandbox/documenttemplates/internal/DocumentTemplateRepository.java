package com.gurch.sandbox.documenttemplates.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentTemplateRepository
    extends ListCrudRepository<DocumentTemplateEntity, Long> {
  Optional<DocumentTemplateEntity> findByTenantIdAndTemplateKey(
      Integer tenantId, String templateKey);

  Optional<DocumentTemplateEntity> findByTenantIdIsNullAndTemplateKey(String templateKey);

  List<DocumentTemplateEntity> findAllByTemplateKey(String templateKey);
}
