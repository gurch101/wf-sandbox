package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.requesttypes.dto.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.dto.RequestTypeSearchCriteria;
import com.gurch.sandbox.requesttypes.dto.RequestTypeSearchResponse;
import com.gurch.sandbox.requesttypes.dto.ResolvedRequestTypeVersion;

/** Public API for request type and version management. */
public interface RequestTypeApi {

  /**
   * Resolves the latest active version for a type key.
   *
   * @param typeKey request type key
   * @return resolved active version
   */
  ResolvedRequestTypeVersion resolveLatestActive(String typeKey);

  /**
   * Resolves a specific version for a type key.
   *
   * @param typeKey request type key
   * @param version immutable version number
   * @return resolved version
   */
  ResolvedRequestTypeVersion resolveVersion(String typeKey, Integer version);

  /**
   * Creates a request type with initial active version.
   *
   * @param command create command
   * @return created active version
   */
  ResolvedRequestTypeVersion createType(RequestTypeCommand command);

  /**
   * Appends and activates a new version for an existing type.
   *
   * @param typeKey request type key
   * @param command change command
   * @return newly activated version
   */
  ResolvedRequestTypeVersion changeType(String typeKey, RequestTypeCommand command);

  /**
   * Searches request types with optional filters.
   *
   * @param criteria search criteria
   * @return matching request types
   */
  PagedResponse<RequestTypeSearchResponse> search(RequestTypeSearchCriteria criteria);

  /**
   * Deletes request type and versions if not used by any request.
   *
   * @param typeKey request type key
   */
  void deleteType(String typeKey);
}
