package com.gurch.sandbox.forms.internal;

import com.gurch.sandbox.forms.FormDocumentType;
import com.gurch.sandbox.forms.FormSignatureStatus;
import com.gurch.sandbox.forms.FormStorageProviderType;
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
@Table("forms_files")
public class FormFileEntity {
  @Id private Long id;
  private String name;
  private String description;
  private String mimeType;
  private Long contentSize;
  private String checksumSha256;
  private FormDocumentType documentType;
  private FormStorageProviderType storageProvider;
  private String storagePath;
  private FormSignatureStatus signatureStatus;
  private String signatureEnvelopeId;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
  @Version private Long version;
}
