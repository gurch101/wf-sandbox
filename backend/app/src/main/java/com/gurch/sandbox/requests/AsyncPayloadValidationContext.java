package com.gurch.sandbox.requests;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Context passed to async payload validators.
 *
 * @param requestId request id
 * @param requestTypeKey request type key
 * @param requestTypeVersion resolved request type version
 * @param payload request payload
 */
@Schema(description = "Async payload validation context")
public record AsyncPayloadValidationContext(
    @Schema(description = "Request id", example = "123") Long requestId,
    @Schema(description = "Request type key", example = "loan") String requestTypeKey,
    @Schema(description = "Resolved request type version", example = "1")
        Integer requestTypeVersion,
    @Schema(description = "Request payload") JsonNode payload) {}
