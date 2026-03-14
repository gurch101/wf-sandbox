package com.gurch.sandbox.policies;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class PolicyAdminController {

  private final PolicyAdminApi policyAdminApi;

  @GetMapping("/{policyId}/capabilities")
  public PolicyCapabilitiesResponse getCapabilities(@PathVariable Long policyId) {
    return policyAdminApi.getCapabilities(policyId);
  }
}
