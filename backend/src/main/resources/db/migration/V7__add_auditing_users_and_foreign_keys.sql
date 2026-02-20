CREATE TABLE users (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_by INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO users (id, username, email, active, created_by, created_at, updated_by, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (1, 'system', 'system@sandbox.local', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);

ALTER TABLE users
  ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);

ALTER TABLE requests
  ADD COLUMN created_by INTEGER NOT NULL,
  ADD COLUMN updated_by INTEGER NOT NULL;
ALTER TABLE requests
  ADD CONSTRAINT fk_requests_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);

ALTER TABLE api_idempotency_records
  ADD COLUMN created_by INTEGER NOT NULL;
ALTER TABLE api_idempotency_records
  ADD CONSTRAINT fk_api_idempotency_records_created_by
      FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE request_tasks
  ADD COLUMN created_by INTEGER NOT NULL,
  ADD COLUMN updated_by INTEGER NOT NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE request_tasks
  ADD CONSTRAINT fk_request_tasks_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_request_tasks_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);

ALTER TABLE forms
  ADD COLUMN created_by INTEGER NOT NULL,
  ADD COLUMN updated_by INTEGER NOT NULL;
ALTER TABLE forms
  ADD CONSTRAINT fk_forms_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_forms_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);

ALTER TABLE request_types
  ADD COLUMN created_by INTEGER NOT NULL,
  ADD COLUMN updated_by INTEGER NOT NULL;
ALTER TABLE request_types
  ADD CONSTRAINT fk_request_types_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_request_types_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);

ALTER TABLE request_type_versions
  RENAME COLUMN version TO type_version;

ALTER TABLE request_type_versions
  ADD COLUMN created_by INTEGER NOT NULL,
  ADD COLUMN updated_by INTEGER NOT NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE request_type_versions
  ADD CONSTRAINT fk_request_type_versions_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_request_type_versions_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
