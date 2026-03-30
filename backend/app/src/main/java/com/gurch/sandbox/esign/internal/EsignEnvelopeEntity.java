package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.persistence.MutableEntity;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("esign_envelopes")
public class EsignEnvelopeEntity extends MutableEntity<Long> {
  private String externalEnvelopeId;
  private String subject;
  private String message;
  private EsignDeliveryMode deliveryMode;
  private EsignEnvelopeStatus status;
  private Integer tenantId;
  private Long sourceStorageObjectId;
  private Long signedStorageObjectId;
  private Long certificateStorageObjectId;
  private boolean remindersEnabled;
  private Integer reminderIntervalHours;
  private String voidedReason;
  private Instant completedAt;
  private Instant lastProviderUpdateAt;
}
