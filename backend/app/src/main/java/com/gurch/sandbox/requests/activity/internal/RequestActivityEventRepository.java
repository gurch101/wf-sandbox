package com.gurch.sandbox.requests.activity.internal;

import com.gurch.sandbox.requests.activity.internal.models.RequestActivityEventEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestActivityEventRepository
    extends ListCrudRepository<RequestActivityEventEntity, Long> {}
