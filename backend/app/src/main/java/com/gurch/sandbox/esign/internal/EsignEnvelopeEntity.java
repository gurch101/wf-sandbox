package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.persistence.MutableEntity;
import com.gurch.sandbox.storage.StorageProviderType;
import java.time.Instant;
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
@Table("esign_envelopes")
public class EsignEnvelopeEntity extends MutableEntity<Long> {
  private String externalEnvelopeId;
  private String provider;
  private String subject;
  private String message;
  private EsignDeliveryMode deliveryMode;
  private EsignEnvelopeStatus status;
  private Integer tenantId;
  private String sourceFileName;
  private String sourceMimeType;
  private Long sourceContentSize;
  private String sourceChecksumSha256;
  private StorageProviderType sourceStorageProvider;
  private String sourceStoragePath;
  private String signedFileName;
  private String signedMimeType;
  private Long signedContentSize;
  private String signedChecksumSha256;
  private StorageProviderType signedStorageProvider;
  private String signedStoragePath;
  private String certificateFileName;
  private String certificateMimeType;
  private Long certificateContentSize;
  private String certificateChecksumSha256;
  private StorageProviderType certificateStorageProvider;
  private String certificateStoragePath;
  private boolean remindersEnabled;
  private Integer reminderIntervalHours;
  private String voidedReason;
  private Instant completedAt;
  private Instant lastProviderUpdateAt;
}
