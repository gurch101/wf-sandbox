CREATE TABLE api_idempotency_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(255) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL, -- 'PROCESSING', 'COMPLETED'
    response_status INT,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (idempotency_key, operation)
);

CREATE INDEX idx_idempotency_key_operation ON api_idempotency_records(idempotency_key, operation);
CREATE INDEX idx_idempotency_created_at ON api_idempotency_records(created_at);
