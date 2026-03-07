CREATE TABLE audit_log_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  resource_type VARCHAR(100) NOT NULL,
  resource_id VARCHAR(100) NOT NULL,
  action VARCHAR(16) NOT NULL,
  actor_user_id INTEGER REFERENCES users(id),
  tenant_id INTEGER REFERENCES tenants(id),
  correlation_id VARCHAR(128),
  before_state JSONB,
  after_state JSONB,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT chk_audit_log_events_action
      CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
  CONSTRAINT chk_audit_log_events_state_by_action
      CHECK (
          (action = 'CREATE' AND before_state IS NULL AND after_state IS NOT NULL)
          OR (action = 'UPDATE' AND before_state IS NOT NULL AND after_state IS NOT NULL)
          OR (action = 'DELETE' AND before_state IS NOT NULL AND after_state IS NULL)
      )
);

CREATE INDEX idx_audit_log_events_resource_time
    ON audit_log_events(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_log_events_actor_time
    ON audit_log_events(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_events_tenant_time
    ON audit_log_events(tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_events_correlation
    ON audit_log_events(correlation_id)
    WHERE correlation_id IS NOT NULL;
