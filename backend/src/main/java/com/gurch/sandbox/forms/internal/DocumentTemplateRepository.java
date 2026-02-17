package com.gurch.sandbox.forms.internal;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentTemplateRepository
    extends ListCrudRepository<DocumentTemplateEntity, Long> {}
