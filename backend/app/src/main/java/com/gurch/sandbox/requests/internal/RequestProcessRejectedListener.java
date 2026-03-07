package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.activity.RequestActivityApi;
import lombok.RequiredArgsConstructor;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("requestProcessRejectedListener")
@RequiredArgsConstructor
public class RequestProcessRejectedListener implements ExecutionListener {

  private static final String REQUESTS_RESOURCE_TYPE = "requests";

  private final RequestRepository requestRepository;
  private final AuditLogApi auditLogApi;
  private final RequestActivityApi requestActivityApi;

  @Override
  @Transactional
  public void notify(DelegateExecution execution) {
    String businessKey = execution.getProcessBusinessKey();
    if (businessKey == null) {
      return;
    }

    Long requestId = Long.valueOf(businessKey);
    requestRepository
        .findById(requestId)
        .ifPresent(
            entity -> {
              RequestEntity updated =
                  requestRepository.save(entity.toBuilder().status(RequestStatus.REJECTED).build());
              String correlationId = execution.getProcessInstanceId();
              auditLogApi.recordUpdate(
                  REQUESTS_RESOURCE_TYPE, requestId, entity, updated, correlationId);
              requestActivityApi.recordStatusChanged(
                  requestId,
                  entity.getStatus(),
                  updated.getStatus(),
                  null,
                  execution.getProcessInstanceId(),
                  correlationId);
            });
  }
}
