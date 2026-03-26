ALTER TABLE document_templates RENAME COLUMN name TO en_name;
ALTER TABLE document_templates RENAME COLUMN description TO en_description;

ALTER TABLE document_templates
  ADD COLUMN fr_name VARCHAR(255),
  ADD COLUMN fr_description TEXT;

DROP INDEX IF EXISTS idx_document_templates_upper_name;

CREATE INDEX idx_document_templates_upper_en_name_prefix
  ON document_templates ((upper(en_name)) text_pattern_ops);

CREATE INDEX idx_document_templates_upper_fr_name_prefix
  ON document_templates ((upper(fr_name)) text_pattern_ops);
