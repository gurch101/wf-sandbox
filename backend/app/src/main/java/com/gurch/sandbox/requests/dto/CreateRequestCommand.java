package com.gurch.sandbox.requests.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Command payload for request create/update draft submission operations. */
@Value
@Builder
@Schema(description = "Command for request submission")
public class CreateRequestCommand {
  @Schema(description = "Request type key", example = "loan")
  String requestTypeKey;
}
