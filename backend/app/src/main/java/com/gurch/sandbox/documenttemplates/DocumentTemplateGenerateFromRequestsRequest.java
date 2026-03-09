package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Value;

/** Generate command for composing merged output from one or more requests. */
@Value
@Schema(description = "Generate command for composing merged output from one or more requests")
public class DocumentTemplateGenerateFromRequestsRequest {
  @NotEmpty
  @Schema(description = "Ordered request identifiers", example = "[101, 102]")
  List<@NotNull Long> requestIds;
}
