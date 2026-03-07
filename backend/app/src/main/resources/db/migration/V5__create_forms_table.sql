CREATE TABLE forms (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  mime_type VARCHAR(255) NOT NULL,
  content_size BIGINT NOT NULL,
  checksum_sha256 VARCHAR(64) NOT NULL,
  document_type VARCHAR(32) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL,
  storage_path TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_forms_upper_name ON forms(upper(name));
CREATE INDEX idx_forms_upper_mime_type ON forms(upper(mime_type));
CREATE INDEX idx_forms_document_type ON forms(document_type);
CREATE INDEX idx_forms_created_at_desc ON forms(created_at DESC);
