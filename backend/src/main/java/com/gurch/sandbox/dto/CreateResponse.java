package com.gurch.sandbox.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

@Value
@Schema(description = "Response containing the ID of a newly created resource")
public class CreateResponse {
  @Schema(description = "Unique identifier of the created resource", example = "123")
  Long id;
}
