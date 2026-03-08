ALTER TABLE document_templates
  ADD COLUMN template_key VARCHAR(200);

CREATE INDEX idx_document_templates_template_key
  ON document_templates(template_key);

CREATE UNIQUE INDEX uq_document_templates_tenant_template_key
  ON document_templates(tenant_id, template_key)
  WHERE tenant_id IS NOT NULL AND template_key IS NOT NULL;

CREATE UNIQUE INDEX uq_document_templates_global_template_key
  ON document_templates(template_key)
  WHERE tenant_id IS NULL AND template_key IS NOT NULL;
