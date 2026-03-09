package com.gurch.sandbox.documenttemplates.internal;

import java.util.Map;

public final class TemplateRenderSource {
  private final String mimeType;
  private final byte[] content;
  private final Map<String, Object> fields;

  public TemplateRenderSource(String mimeType, byte[] content, Map<String, Object> fields) {
    this.mimeType = mimeType;
    this.content = content == null ? new byte[0] : content.clone();
    this.fields = fields == null ? Map.of() : Map.copyOf(fields);
  }

  public String getMimeType() {
    return mimeType;
  }

  public byte[] getContent() {
    return content.clone();
  }

  public Map<String, Object> getFields() {
    return fields;
  }
}
