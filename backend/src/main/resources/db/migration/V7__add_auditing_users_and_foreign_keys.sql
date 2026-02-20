CREATE TABLE users (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO users (id, username, display_name, active, created_by, created_at)
OVERRIDING SYSTEM VALUE
VALUES (1, 'system', 'System', TRUE, NULL, CURRENT_TIMESTAMP);

ALTER TABLE users
  ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id);

CREATE INDEX idx_users_created_by ON users(created_by);

ALTER TABLE requests
  ADD COLUMN created_by BIGINT NOT NULL,
  ADD COLUMN updated_by BIGINT NOT NULL;
ALTER TABLE requests
  ADD CONSTRAINT fk_requests_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
CREATE INDEX idx_requests_created_by ON requests(created_by);
CREATE INDEX idx_requests_updated_by ON requests(updated_by);

ALTER TABLE api_idempotency_records
  ADD COLUMN created_by BIGINT NOT NULL;
ALTER TABLE api_idempotency_records
  ADD CONSTRAINT fk_api_idempotency_records_created_by
      FOREIGN KEY (created_by) REFERENCES users(id);
CREATE INDEX idx_api_idempotency_records_created_by ON api_idempotency_records(created_by);

ALTER TABLE request_tasks
  ADD COLUMN created_by BIGINT NOT NULL;
ALTER TABLE request_tasks
  ADD CONSTRAINT fk_request_tasks_created_by FOREIGN KEY (created_by) REFERENCES users(id);
CREATE INDEX idx_request_tasks_created_by ON request_tasks(created_by);

ALTER TABLE forms
  ADD COLUMN created_by BIGINT NOT NULL,
  ADD COLUMN updated_by BIGINT NOT NULL;
ALTER TABLE forms
  ADD CONSTRAINT fk_forms_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_forms_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
CREATE INDEX idx_forms_created_by ON forms(created_by);
CREATE INDEX idx_forms_updated_by ON forms(updated_by);

ALTER TABLE request_types
  ADD COLUMN created_by BIGINT NOT NULL,
  ADD COLUMN updated_by BIGINT NOT NULL;
ALTER TABLE request_types
  ADD CONSTRAINT fk_request_types_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_request_types_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
CREATE INDEX idx_request_types_created_by ON request_types(created_by);
CREATE INDEX idx_request_types_updated_by ON request_types(updated_by);

ALTER TABLE request_type_versions
  ADD COLUMN created_by BIGINT NOT NULL,
  ADD COLUMN updated_by BIGINT NOT NULL;
ALTER TABLE request_type_versions
  ADD CONSTRAINT fk_request_type_versions_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_request_type_versions_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
CREATE INDEX idx_request_type_versions_created_by ON request_type_versions(created_by);
CREATE INDEX idx_request_type_versions_updated_by ON request_type_versions(updated_by);
