package com.gurch.sandbox.requests.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestTaskRepository extends ListCrudRepository<RequestTaskEntity, Long> {
  List<RequestTaskEntity> findByRequestId(Long requestId);

  Optional<RequestTaskEntity> findByTaskId(String taskId);
}
