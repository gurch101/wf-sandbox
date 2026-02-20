package com.gurch.sandbox.forms.internal;

import com.gurch.sandbox.forms.DocumentTemplateType;
import com.gurch.sandbox.persistence.VersionedAuditableEntity;
import com.gurch.sandbox.storage.StorageProviderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Table("forms")
public class DocumentTemplateEntity extends VersionedAuditableEntity<Long> {
  private String name;
  private String description;
  private String mimeType;
  private Long contentSize;
  private String checksumSha256;
  private DocumentTemplateType documentType;
  private StorageProviderType storageProvider;
  private String storagePath;
}
