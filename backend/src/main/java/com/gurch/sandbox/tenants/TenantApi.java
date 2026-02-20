package com.gurch.sandbox.tenants;

import java.util.List;
import java.util.Optional;

/** Public API for admin tenant CRUD and search operations. */
public interface TenantApi {

  /** Returns one tenant by identifier. */
  Optional<TenantResponse> findById(Integer id);

  /** Creates a new tenant. */
  Integer create(TenantCommand command);

  /** Updates an existing tenant. */
  Integer update(Integer id, TenantCommand command, Long version);

  /** Deletes an existing tenant. */
  void deleteById(Integer id);

  /** Searches tenants using optional filters. */
  List<TenantSearchResponse> search(TenantSearchCriteria criteria);
}
