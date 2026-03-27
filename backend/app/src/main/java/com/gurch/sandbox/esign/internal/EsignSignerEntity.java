package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignSignerStatus;
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
@Table("esign_signers")
public class EsignSignerEntity extends MutableEntity<Long> {
  private Long envelopeId;
  private String roleKey;
  private String signatureAnchorText;
  private String dateAnchorText;
  private Integer routingOrder;
  private String fullName;
  private String email;
  private String phoneNumber;
  private EsignAuthMethod authMethod;
  private String smsNumber;
  private String providerRecipientId;
  private EsignSignerStatus status;
  private Instant viewedAt;
  private Instant completedAt;
  private Instant lastStatusAt;
}
