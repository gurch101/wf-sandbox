package com.gurch.sandbox.documenttemplates.dto;

import com.gurch.sandbox.documenttemplates.DocumentTemplateLanguage;
import java.io.InputStream;
import lombok.Value;

/** Upload command for a document template. */
@Value
public class DocumentTemplateUploadCommand {
  String enName;
  String frName;
  String enDescription;
  String frDescription;
  DocumentTemplateLanguage language;
  Integer tenantId;
  String originalFilename;
  String mimeType;
  Long contentSize;
  InputStream contentStream;
}
