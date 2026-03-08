# Reference: Idempotency

## Aspect Scope

`IdempotencyAspect` applies to controller methods matched by:

- `@PostMapping`
- `@PutMapping`
- `@DeleteMapping`

Excluded when method has `@NotIdempotent`.

## Required Header

- `Idempotency-Key` (request header)

Missing header triggers `MissingIdempotencyKeyException`.

## Operation Identity

Idempotency record key space is `(idempotency_key, operation)` where:

- `operation = "<HTTP_METHOD> <requestURI>"`

## Request Hash

- Hash algorithm: SHA-256
- Source payload: method argument annotated with `@RequestBody`
- Stored as Base64 string in `request_hash`

If no `@RequestBody` argument exists, payload hash is empty string.

## Persistence Model

Table: `api_idempotency_records`

Columns:

- `idempotency_key` (`VARCHAR(128)`, required)
- `operation` (`VARCHAR(255)`, required)
- `request_hash` (`VARCHAR(128)`, required)
- `status` (`PROCESSING|COMPLETED`, required)
- `response_status` (`INT`, nullable)
- `response_body` (`JSONB`, nullable)
- `created_at` (`TIMESTAMPTZ`, required)

Constraints/Indexes:

- Unique `(idempotency_key, operation)`
- Index on `(idempotency_key, operation)`
- Index on `created_at`

## Replay Semantics

- Same key + same operation + same hash + status COMPLETED -> replay stored response
- Same key + same operation + different hash -> `IdempotencyConflictException`
- Same key + same operation + status PROCESSING -> `IdempotencyConflictException`

## Transaction Model

`IdempotencyService` uses `REQUIRES_NEW` transactions for:

- start operation
- complete operation
- delete failed operation

This isolates idempotency record visibility from domain transaction boundaries.

## Cleanup

`IdempotencyCleanupTask` removes old records based on retention policy configured in the app.
