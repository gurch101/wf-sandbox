INSERT INTO tenants (id, name, active, created_by, created_at, updated_by, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (1, 'default-tenant', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);

INSERT INTO users (id, username, email, active, created_by, created_at, updated_by, updated_at)
OVERRIDING SYSTEM VALUE
VALUES
  (2, 'alice', 'alice@sandbox.local', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP),
  (3, 'bob', 'bob@sandbox.local', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);

UPDATE users
SET tenant_id = 1
WHERE id = 2;

SELECT setval(
  pg_get_serial_sequence('tenants', 'id'),
  GREATEST((SELECT COALESCE(MAX(id), 1) FROM tenants), 1),
  true
);

SELECT setval(
  pg_get_serial_sequence('users', 'id'),
  GREATEST((SELECT COALESCE(MAX(id), 1) FROM users), 1),
  true
);
