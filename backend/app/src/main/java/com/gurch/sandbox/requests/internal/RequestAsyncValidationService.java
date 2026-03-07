package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.requests.AsyncPayloadValidationContext;
import com.gurch.sandbox.requests.AsyncPayloadValidationResult;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.ResolvedRequestTypeVersion;
import com.gurch.sandbox.web.NotFoundException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Service that resolves and executes async payload validators for a request. */
@Service
@RequiredArgsConstructor
public class RequestAsyncValidationService {

  private final RequestApi requestApi;
  private final RequestTypeApi requestTypeApi;
  private final AsyncPayloadValidatorRegistry asyncPayloadValidatorRegistry;

  public Map<String, Object> validate(Long requestId) {
    RequestResponse request =
        requestApi
            .findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found with id: " + requestId));
    if (request.getPayload() == null) {
      return Map.of("asyncValidationPassed", false, "asyncValidationError", "payload is missing");
    }

    ResolvedRequestTypeVersion resolved =
        requestTypeApi.resolveVersion(request.getRequestTypeKey(), request.getRequestTypeVersion());
    AsyncPayloadValidationContext context =
        new AsyncPayloadValidationContext(
            request.getId(),
            request.getRequestTypeKey(),
            request.getRequestTypeVersion(),
            request.getPayload());
    AsyncPayloadValidationResult result =
        asyncPayloadValidatorRegistry.validate(
            resolved.getPayloadHandlerId(), request.getPayload(), context);

    Map<String, Object> variables = new HashMap<>();
    variables.put("asyncValidationPassed", result.passed());
    if (!result.passed() && result.reason() != null && !result.reason().isBlank()) {
      variables.put("asyncValidationError", result.reason());
    }
    return variables;
  }
}
