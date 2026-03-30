package com.gurch.sandbox.tenants;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.tenants.dto.TenantCommand;
import com.gurch.sandbox.tenants.dto.TenantResponse;
import com.gurch.sandbox.tenants.dto.TenantSearchCriteria;
import com.gurch.sandbox.tenants.dto.TenantSearchResponse;
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
  PagedResponse<TenantSearchResponse> search(TenantSearchCriteria criteria);
}
