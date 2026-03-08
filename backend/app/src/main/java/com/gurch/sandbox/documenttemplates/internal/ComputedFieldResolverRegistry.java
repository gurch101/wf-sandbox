package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Registry for computed-field resolvers used by request-driven generation mappings. */
@Component
public class ComputedFieldResolverRegistry {

  private final Map<String, ComputedFieldResolver> resolvers;

  public ComputedFieldResolverRegistry(List<ComputedFieldResolver> resolvers) {
    this.resolvers =
        resolvers.stream()
            .collect(Collectors.toMap(ComputedFieldResolver::id, Function.identity()));
  }

  public JsonNode resolve(String resolverId, JsonNode input, Integer tenantId) {
    ComputedFieldResolver resolver = resolvers.get(resolverId);
    if (resolver == null) {
      throw new IllegalArgumentException("Unknown computed resolver id: " + resolverId);
    }
    return resolver.resolve(input, tenantId);
  }
}
