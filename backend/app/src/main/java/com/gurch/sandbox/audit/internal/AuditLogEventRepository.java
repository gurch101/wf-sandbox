package com.gurch.sandbox.audit.internal;

import com.gurch.sandbox.audit.internal.models.AuditLogEventEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogEventRepository extends ListCrudRepository<AuditLogEventEntity, Long> {}
