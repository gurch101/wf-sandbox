CREATE TABLE request_tasks (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
  process_instance_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  assignee VARCHAR(255),
  action VARCHAR(32),
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_request_tasks_request_id ON request_tasks(request_id);
CREATE INDEX idx_request_tasks_assignee ON request_tasks(assignee);
CREATE INDEX idx_request_tasks_status ON request_tasks(status);
