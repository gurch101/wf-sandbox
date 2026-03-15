package com.gurch.sandbox.requesttypes.internal;

import com.gurch.sandbox.persistence.BaseEntity;
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
@Table("workflow_model_input_references")
public class WorkflowModelInputReferenceEntity extends BaseEntity<Long> {

  @Column("request_type_version_id")
  private Long requestTypeVersionId;

  private String processDefinitionKey;

  @Column("bpmn_element_id")
  private String bpmnElementId;

  private String referenceKind;
  private String inputKey;
}
