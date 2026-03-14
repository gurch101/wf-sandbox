package com.gurch.sandbox.policies;

/** Public API for policy admin capabilities discovery endpoints. */
public interface PolicyAdminApi {

  /**
   * Returns the rule-authoring capability contract for one policy version.
   *
   * @param policyId policy version id
   * @return capabilities response
   */
  PolicyCapabilitiesResponse getCapabilities(Long policyId);
}
