CREATE TABLE requests (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL,
  process_instance_id VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL
);

CREATE INDEX idx_requests_status ON requests(status);
CREATE INDEX idx_requests_upper_name ON requests(upper(name));
CREATE INDEX idx_requests_status_created_at_desc ON requests(status, created_at DESC);
