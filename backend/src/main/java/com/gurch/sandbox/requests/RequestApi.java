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
   * Creates a new request record.
   *
   * @param name the name of the request
   * @param status the initial status of the request
   * @return the created request response
   */
  RequestResponse create(String name, RequestStatus status);

  /**
   * Updates an existing request record. Implements optimistic locking via the version field.
   *
   * @param id the unique identifier of the request to update
   * @param name the new name for the request
   * @param status the new status for the request
   * @param version the current version of the record for optimistic locking
   * @return the updated request response
   * @throws com.gurch.sandbox.web.NotFoundException if the request does not exist
   * @throws org.springframework.dao.OptimisticLockingFailureException if the version is stale
   */
  RequestResponse update(Long id, String name, RequestStatus status, Long version);

  /**
   * Deletes a request record by its unique identifier.
   *
   * @param id the unique identifier of the request to delete
   */
  void deleteById(Long id);

  /**
   * Searches for requests matching the provided criteria. Supports partial name match, status
   * filtering, ID filtering, and pagination.
   *
   * @param criteria the search criteria to apply
   * @return a list of request responses matching the criteria
   */
  List<RequestResponse> search(RequestSearchCriteria criteria);
}
