package com.gurch.sandbox.documenttemplates.internal;

import com.gurch.sandbox.documenttemplates.DocumentTemplateLanguage;
import com.gurch.sandbox.persistence.MutableEntity;
import com.gurch.sandbox.storage.StorageProviderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("document_templates")
public class DocumentTemplateEntity extends MutableEntity<Long> {
  private String enName;
  private String frName;
  private String enDescription;
  private String frDescription;
  private String mimeType;
  private Long contentSize;
  private String checksumSha256;
  private DocumentTemplateLanguage language;
  private Integer tenantId;

  @Column("form_map_json")
  private String formMapJson;

  private boolean esignable;
  private StorageProviderType storageProvider;
  private String storagePath;
}
