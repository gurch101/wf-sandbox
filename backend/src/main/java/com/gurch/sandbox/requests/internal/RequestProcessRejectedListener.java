package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.requests.RequestStatus;
import lombok.RequiredArgsConstructor;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("requestProcessRejectedListener")
@RequiredArgsConstructor
public class RequestProcessRejectedListener implements ExecutionListener {

  private final RequestRepository requestRepository;

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
            entity ->
                requestRepository.save(entity.toBuilder().status(RequestStatus.REJECTED).build()));
  }
}
