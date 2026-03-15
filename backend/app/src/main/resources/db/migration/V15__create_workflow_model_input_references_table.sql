CREATE TABLE workflow_model_input_references (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_type_version_id BIGINT NOT NULL
      REFERENCES request_type_versions(id) ON DELETE CASCADE,
  process_definition_key VARCHAR(100) NOT NULL,
  bpmn_element_id VARCHAR(255) NOT NULL,
  reference_kind VARCHAR(64) NOT NULL,
  input_key VARCHAR(255) NOT NULL,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (request_type_version_id, bpmn_element_id, reference_kind, input_key)
);

CREATE INDEX idx_workflow_model_input_references_request_type_version
    ON workflow_model_input_references(request_type_version_id);

CREATE INDEX idx_workflow_model_input_references_process_definition
    ON workflow_model_input_references(process_definition_key);
