package com.gurch.sandbox.requesttypes;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/request-types")
@RequiredArgsConstructor
public class RequestTypeModelerCapabilitiesController {

  private final RequestTypeModelerCapabilitiesApi requestTypeModelerCapabilitiesApi;

  @GetMapping("/{typeKey}/versions/{version}/modeler-capabilities")
  public RequestTypeModelerCapabilitiesResponse getCapabilities(
      @PathVariable String typeKey, @PathVariable Integer version) {
    return requestTypeModelerCapabilitiesApi.getCapabilities(typeKey, version);
  }
}
