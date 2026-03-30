package com.gurch.sandbox.users;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.users.dto.UserCommand;
import com.gurch.sandbox.users.dto.UserResponse;
import com.gurch.sandbox.users.dto.UserSearchCriteria;
import com.gurch.sandbox.users.dto.UserSearchResponse;
import java.util.Optional;

/** Public API for admin user CRUD and search operations. */
public interface UserApi {

  /** Returns one user by identifier. */
  Optional<UserResponse> findById(Integer id);

  /** Creates a new user. */
  Integer create(UserCommand command);

  /** Updates an existing user. */
  Integer update(Integer id, UserCommand command, Long version);

  /** Deletes an existing user. */
  void deleteById(Integer id);

  /** Searches users using optional filters. */
  PagedResponse<UserSearchResponse> search(UserSearchCriteria criteria);
}
