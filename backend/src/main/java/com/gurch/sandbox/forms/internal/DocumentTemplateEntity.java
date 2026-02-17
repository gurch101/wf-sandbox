package com.gurch.sandbox.forms.internal;

import com.gurch.sandbox.forms.DocumentTemplateType;
import com.gurch.sandbox.storage.StorageProviderType;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@Table("forms")
public class DocumentTemplateEntity {
  @Id private Long id;
  private String name;
  private String description;
  private String mimeType;
  private Long contentSize;
  private String checksumSha256;
  private DocumentTemplateType documentType;
  private StorageProviderType storageProvider;
  private String storagePath;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
  @Version private Long version;
}
