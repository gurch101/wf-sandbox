package com.gurch.sandbox.requesttypes.internal;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestTypeVersionRepository
    extends ListCrudRepository<RequestTypeVersionEntity, Long> {}
