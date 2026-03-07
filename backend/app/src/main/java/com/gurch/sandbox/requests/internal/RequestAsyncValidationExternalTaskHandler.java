package com.gurch.sandbox.requests.internal;

import lombok.RequiredArgsConstructor;
import org.finos.fluxnova.bpm.engine.ExternalTaskService;
import org.finos.fluxnova.bpm.engine.externaltask.LockedExternalTask;
import org.springframework.stereotype.Component;

/**
 * Fluxnova adapter for async request validation external tasks.
 *
 * <p>Call {@link #handle(LockedExternalTask)} from an external-task worker loop.
 */
@Component
@RequiredArgsConstructor
public class RequestAsyncValidationExternalTaskHandler {

  private final ExternalTaskService externalTaskService;
  private final RequestAsyncValidationService requestAsyncValidationService;

  public void handle(LockedExternalTask task) {
    Long requestId = Long.valueOf(task.getBusinessKey());
    externalTaskService.complete(
        task.getId(), task.getWorkerId(), requestAsyncValidationService.validate(requestId));
  }
}
