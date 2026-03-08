# How-to: Make a Mutating Endpoint Idempotent

## Goal

Ensure a POST/PUT/DELETE endpoint safely handles retries and duplicate submissions.

## Prerequisites

- Endpoint uses Spring MVC mapping annotation (`@PostMapping`, `@PutMapping`, `@DeleteMapping`)
- Endpoint accepts deterministic request body for replay-safe behavior

## Procedure

1. Implement endpoint without `@NotIdempotent`.
2. Require callers to send `Idempotency-Key` header.
3. Keep response deterministic for same input.
4. Ensure failed operations do not leave stale `PROCESSING` records.

## Runtime Behavior Summary

- Missing `Idempotency-Key` -> request rejected.
- First request for `(key, method+path)` -> record created as `PROCESSING`.
- Successful completion -> record marked `COMPLETED` with response status/body.
- Replay with same key + same payload hash -> stored response returned.
- Replay with same key + different payload hash -> conflict.
- Concurrent replay while `PROCESSING` -> conflict.

## Developer Checklist

- [ ] Endpoint is not annotated with `@NotIdempotent`
- [ ] Public docs/API clients include `Idempotency-Key` requirement
- [ ] Tests cover same key same payload and same key different payload
- [ ] Response body can be serialized/deserialized by `ObjectMapper`

## Troubleshooting

- Unexpected conflict on replay:
  - Confirm payload bytes are identical (field ordering/serialization differences can matter).
- Missing replay behavior:
  - Confirm endpoint uses POST/PUT/DELETE and is not annotated with `@NotIdempotent`.
- Missing header errors in clients:
  - Ensure OpenAPI consumers send `Idempotency-Key` on mutating operations.
