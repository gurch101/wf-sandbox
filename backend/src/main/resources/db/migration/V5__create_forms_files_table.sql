CREATE TABLE forms_files (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  mime_type VARCHAR(255) NOT NULL,
  content_size BIGINT NOT NULL,
  checksum_sha256 VARCHAR(64) NOT NULL,
  document_type VARCHAR(32) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL,
  storage_path TEXT NOT NULL UNIQUE,
  signature_status VARCHAR(32) NOT NULL,
  signature_envelope_id VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_forms_files_upper_name ON forms_files(upper(name));
CREATE INDEX idx_forms_files_upper_mime_type ON forms_files(upper(mime_type));
CREATE INDEX idx_forms_files_document_type ON forms_files(document_type);
CREATE INDEX idx_forms_files_signature_status ON forms_files(signature_status);
CREATE INDEX idx_forms_files_created_at_desc ON forms_files(created_at DESC);
