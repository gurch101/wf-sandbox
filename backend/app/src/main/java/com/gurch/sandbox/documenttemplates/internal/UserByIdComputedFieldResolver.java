package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gurch.sandbox.users.UserApi;
import com.gurch.sandbox.users.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves user details from a numeric user identifier. */
@Component
@RequiredArgsConstructor
public class UserByIdComputedFieldResolver implements ComputedFieldResolver {

  private static final String ID = "user-by-id";

  private final UserApi userApi;
  private final ObjectMapper objectMapper;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public JsonNode resolve(JsonNode input, Integer tenantId) {
    if (input == null || input.isNull()) {
      throw new IllegalArgumentException("Computed resolver 'user-by-id' input is required");
    }
    if (!input.canConvertToInt()) {
      throw new IllegalArgumentException("Computed resolver 'user-by-id' input must be numeric");
    }
    Integer userId = input.intValue();
    UserResponse user =
        userApi
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found for id: " + userId));
    if (tenantId != null && user.getTenantId() != null && !tenantId.equals(user.getTenantId())) {
      throw new IllegalArgumentException(
          "User " + userId + " is outside the authenticated tenant scope");
    }
    ObjectNode output = objectMapper.createObjectNode();
    output.put("id", user.getId());
    output.put("username", user.getUsername());
    output.put("email", user.getEmail());
    output.put("active", user.isActive());
    if (user.getTenantId() == null) {
      output.putNull("tenantId");
    } else {
      output.put("tenantId", user.getTenantId());
    }
    return output;
  }
}
