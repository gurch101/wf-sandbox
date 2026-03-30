package com.gurch.sandbox.requests;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.requests.activity.dto.RequestActivityEventResponse;
import com.gurch.sandbox.requests.activity.dto.RequestActivitySearchCriteria;
import com.gurch.sandbox.requests.dto.CreateRequestCommand;
import com.gurch.sandbox.requests.dto.RequestResponse;
import com.gurch.sandbox.requests.dto.RequestSearchCriteria;
import com.gurch.sandbox.requests.dto.RequestSearchResponse;
import com.gurch.sandbox.requests.tasks.dto.TaskAction;
import java.util.Optional;

/** Public API for request submission, lookup, and search operations. */
public interface RequestApi {

  /**
   * Fetches a request by identifier.
   *
   * @param id request id
   * @return request when present
   */
  Optional<RequestResponse> findById(Long id);

  /**
   * Creates a draft request without validation/workflow processing.
   *
   * @param command draft command
   * @return created draft request id
   */
  Long createDraft(CreateRequestCommand command);

  /**
   * Updates an existing draft request.
   *
   * @param id request id
   * @param version optimistic lock version
   * @return updated draft id
   */
  Long updateDraft(Long id, Long version);

  /**
   * Submits an existing draft request; validation/workflow happen here.
   *
   * @param id request id
   * @return submitted request
   */
  RequestResponse submitDraft(Long id);

  /**
   * Creates and submits a request using latest active request type version.
   *
   * @param command request submission command
   * @return created request
   */
  RequestResponse createAndSubmit(CreateRequestCommand command);

  /**
   * Deletes a request by identifier.
   *
   * @param id request id
   */
  void deleteById(Long id);

  /**
   * Completes a workflow user task for a request.
   *
   * @param requestId request id
   * @param taskId request task id
   * @param action selected task action
   * @param comment optional completion comment
   */
  void completeTask(Long requestId, Long taskId, TaskAction action, String comment);

  /**
   * Returns paged domain activity events for a request.
   *
   * @param id request id
   * @param criteria activity search criteria
   * @return matching activity entries
   */
  PagedResponse<RequestActivityEventResponse> searchActivity(
      Long id, RequestActivitySearchCriteria criteria);

  /**
   * Searches requests using filters and paging options.
   *
   * @param criteria search criteria
   * @return matching requests
   */
  PagedResponse<RequestSearchResponse> search(RequestSearchCriteria criteria);
}
