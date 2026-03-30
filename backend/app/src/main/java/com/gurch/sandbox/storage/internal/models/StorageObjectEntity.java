package com.gurch.sandbox.storage.internal.models;

import com.gurch.sandbox.persistence.BaseEntity;
import com.gurch.sandbox.storage.StorageProviderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("storage_objects")
public class StorageObjectEntity extends BaseEntity<Long> {
  private String fileName;
  private String mimeType;
  private Long contentSize;
  private String checksumSha256;
  private StorageProviderType storageProvider;
  private String storagePath;
}
