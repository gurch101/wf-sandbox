package com.gurch.sandbox.requests;

import java.util.List;
import java.util.Optional;

/**
 * Public API for managing request records. Provides operations for CRUD and searching request data.
 */
public interface RequestApi {
  /**
   * Retrieves all requests in the system.
   *
   * @return a list of all request responses
   */
  List<RequestResponse> findAll();

  /**
   * Finds a specific request by its unique identifier.
   *
   * @param id the unique identifier of the request
   * @return an optional containing the request response if found, or empty if not
   */
  Optional<RequestResponse> findById(Long id);

  /**
   * Creates a new draft request.
   *
   * @param name the name of the request
   * @return the created request response
   */
  RequestResponse createDraft(String name);

  /**
   * Creates and submits a request, starting workflow immediately.
   *
   * @param name the name of the request
   * @return the submitted request response
   */
  RequestResponse createAndSubmit(String name);

  /**
   * Updates an existing draft request. Implements optimistic locking via the version field.
   *
   * @param id the unique identifier of the request to update
   * @param name the new name for the request
   * @param version the current version of the record for optimistic locking
   * @return the updated request response
   */
  RequestResponse updateDraft(Long id, String name, Long version);

  /**
   * Submits an existing draft request and starts workflow.
   *
   * @param id the unique identifier of the request to submit
   * @param name optional updated name to persist before submit
   * @param version optional optimistic-lock version for the update-before-submit path
   * @return the submitted request response
   */
  RequestResponse submitDraft(Long id, String name, Long version);

  /**
   * Deletes a request record by its unique identifier.
   *
   * @param id the unique identifier of the request to delete
   */
  void deleteById(Long id);

  /**
   * Completes a workflow user task for a request.
   *
   * @param requestId request identifier
   * @param taskId request task identifier
   * @param action task action selected by the user
   * @param comment task completion comment
   */
  void completeTask(Long requestId, Long taskId, TaskAction action, String comment);

  /**
   * Searches for requests matching the provided criteria. Supports partial name match, status
   * filtering, ID filtering, and pagination.
   *
   * @param criteria the search criteria to apply
   * @return a list of request responses matching the criteria
   */
  List<RequestResponse> search(RequestSearchCriteria criteria);
}
