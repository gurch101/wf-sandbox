package com.gurch.sandbox.documenttemplates.dto;

import java.io.InputStream;
import lombok.Value;

/** Update command for document template metadata and optional content replacement. */
@Value
public class DocumentTemplateUpdateCommand {
  String enName;
  String frName;
  String enDescription;
  String frDescription;
  String originalFilename;
  String mimeType;
  Long contentSize;
  InputStream contentStream;
}
