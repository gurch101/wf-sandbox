package com.gurch.sandbox.auth;

import java.util.List;
import java.util.UUID;

/** Read-only auth context lookups used by other modules. */
public interface AuthContextApi {

  /** Returns workflow group codes assigned to the principal user. */
  List<String> findWorkflowGroupCodes(UUID userId);

  /** Returns business client scopes assigned to the principal user. */
  List<String> findClientScopeIds(UUID userId);
}
