package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.JsonNode;

/** Resolves computed field values from an input argument. */
public interface ComputedFieldResolver {

  /** Stable resolver identifier used in request-type config mappings. */
  String id();

  /** Resolves a context object from a scalar or structured input argument. */
  JsonNode resolve(JsonNode input, Integer tenantId);
}
