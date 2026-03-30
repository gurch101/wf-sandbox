package com.gurch.sandbox.documenttemplates.internal.models;

import com.gurch.sandbox.documenttemplates.DocumentTemplateLanguage;
import com.gurch.sandbox.persistence.MutableEntity;
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
  private DocumentTemplateLanguage language;
  private Integer tenantId;

  @Column("form_map_json")
  private String formMapJson;

  private String esignAnchorMetadataJson;

  private boolean esignable;
  private Long storageObjectId;
}
