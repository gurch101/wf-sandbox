package com.gurch.sandbox.forms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Download payload containing file metadata plus bytes. */
@Value
@Builder
@Schema(description = "Download payload containing file metadata and content")
public class FormFileDownload {
  @Schema(description = "File name", example = "Client Intake Form.pdf")
  String name;

  @Schema(description = "MIME type", example = "application/pdf")
  String mimeType;

  @Schema(description = "File size in bytes", example = "82944")
  Long contentSize;

  @Schema(description = "Raw file content")
  byte[] content;
}
