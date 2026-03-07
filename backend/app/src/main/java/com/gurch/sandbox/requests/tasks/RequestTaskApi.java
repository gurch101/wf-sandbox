package com.gurch.sandbox.requests.tasks;

import com.gurch.sandbox.requests.tasks.dto.RequestTaskResponse;
import com.gurch.sandbox.requests.tasks.dto.TaskAction;
import java.util.List;

/** API for querying request tasks from other request submodules. */
public interface RequestTaskApi {

  /** Returns all tasks for a request. */
  List<RequestTaskResponse> findByRequestId(Long requestId);

  /** Completes a workflow user task for a request. */
  void completeTask(Long requestId, Long taskId, TaskAction action, String comment);
}
