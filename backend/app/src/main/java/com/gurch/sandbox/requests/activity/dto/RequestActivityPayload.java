package com.gurch.sandbox.requests.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Marker interface for typed request activity payload variants. */
@Schema(
    description = "Typed request activity payload",
    oneOf = {
      RequestStatusChangedActivityPayload.class,
      RequestTaskAssignedActivityPayload.class,
      RequestTaskCompletedActivityPayload.class
    })
public interface RequestActivityPayload {}
