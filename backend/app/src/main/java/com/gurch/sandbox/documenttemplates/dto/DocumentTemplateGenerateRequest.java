package com.gurch.sandbox.documenttemplates.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Generate command for composing merged output from document templates. */
@Value
@Schema(description = "Generate command for composing merged output from document templates")
public class DocumentTemplateGenerateRequest {
  @Valid
  @NotEmpty
  @Schema(description = "Ordered template inputs to render and merge")
  List<GenerateInput> documents;

  /** One template render instruction. */
  @Value
  @Schema(description = "One template render instruction")
  public static class GenerateInput {
    @NotNull
    @Schema(description = "Template identifier", example = "123")
    Long documentTemplateId;

    @Schema(description = "Field map used for PDF form fill or Word placeholder merge")
    Map<String, Object> fields;
  }
}
