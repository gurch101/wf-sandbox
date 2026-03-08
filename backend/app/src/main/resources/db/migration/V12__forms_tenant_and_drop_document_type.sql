ALTER TABLE forms
  ADD COLUMN tenant_id INTEGER;

ALTER TABLE forms
  ADD CONSTRAINT fk_forms_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_forms_tenant_id ON forms(tenant_id);

DROP INDEX IF EXISTS idx_forms_document_type;
ALTER TABLE forms DROP COLUMN IF EXISTS document_type;
