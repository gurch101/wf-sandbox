package com.gurch.sandbox.requesttypes;

/** Public API for request type modeler capability discovery. */
public interface RequestTypeModelerCapabilitiesApi {

  /**
   * Returns the code-defined modeler capabilities for a request type version.
   *
   * @param typeKey request type key
   * @param version immutable request type version
   * @return modeler capabilities
   */
  RequestTypeModelerCapabilitiesResponse getCapabilities(String typeKey, Integer version);
}
