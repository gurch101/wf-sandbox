DROP INDEX IF EXISTS idx_requests_upper_name;
ALTER TABLE requests DROP COLUMN IF EXISTS name;
CREATE INDEX IF NOT EXISTS idx_requests_upper_type_key ON requests(upper(request_type_key));
