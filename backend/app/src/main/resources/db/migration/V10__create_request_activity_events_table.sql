CREATE TABLE request_activity_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
  event_type VARCHAR(64) NOT NULL,
  actor_user_id INTEGER REFERENCES users(id),
  correlation_id VARCHAR(128),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_request_activity_events_request_time
    ON request_activity_events(request_id, created_at DESC);
CREATE INDEX idx_request_activity_events_event_type
    ON request_activity_events(event_type);
