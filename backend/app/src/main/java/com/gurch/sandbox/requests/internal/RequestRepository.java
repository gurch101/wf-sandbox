package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.requests.internal.models.RequestEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestRepository extends ListCrudRepository<RequestEntity, Long> {}
