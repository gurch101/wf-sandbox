package com.gurch.sandbox.forms.internal;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FormFileRepository extends ListCrudRepository<FormFileEntity, Long> {}
