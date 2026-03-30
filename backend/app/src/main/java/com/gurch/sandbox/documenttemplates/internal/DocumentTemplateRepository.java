package com.gurch.sandbox.documenttemplates.internal;

import com.gurch.sandbox.documenttemplates.internal.models.DocumentTemplateEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentTemplateRepository
    extends ListCrudRepository<DocumentTemplateEntity, Long> {}
