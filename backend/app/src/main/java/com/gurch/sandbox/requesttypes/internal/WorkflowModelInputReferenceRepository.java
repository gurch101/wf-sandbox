package com.gurch.sandbox.requesttypes.internal;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowModelInputReferenceRepository
    extends ListCrudRepository<WorkflowModelInputReferenceEntity, Long> {

  void deleteByRequestTypeVersionId(Long requestTypeVersionId);

  List<WorkflowModelInputReferenceEntity> findByRequestTypeVersionId(Long requestTypeVersionId);
}
