# Explanation: Idempotency Design and Tradeoffs

## Context

Client retries, timeouts, and network failures can cause duplicate mutation requests. The app uses
a global aspect-based idempotency layer to make mutating endpoints retry-safe.

## Design

- Interception point: `IdempotencyAspect` around POST/PUT/DELETE controller methods
- Storage: `api_idempotency_records`
- Identity: `Idempotency-Key` + operation (`method + URI`)
- Safety check: payload hash comparison
- Replay: stored response status/body returned for completed duplicates

## Why Aspect-Based

- No repeated idempotency boilerplate in every controller/service method
- Uniform behavior across modules
- Centralized conflict handling and replay logic

## Tradeoffs

Pros:

- Strong duplicate suppression for retry storms
- Consistent client contract with a single required header
- Stored response replay simplifies safe client retries

Cons:

- Payload hash behavior depends on serialized request-body representation
- Endpoints without `@RequestBody` hash to empty string, reducing payload differentiation
- Replay storage can increase table size until cleanup runs

## Operational Notes

- Failed business execution removes `PROCESSING` record, allowing a clean retry.
- Concurrency races are handled via unique constraint + retry/read path.
- `@NotIdempotent` should be used sparingly and explicitly justified.

## Related Docs

- Implementation checklist: [How-to: Make a Mutating Endpoint Idempotent](../how-to/make-a-mutating-endpoint-idempotent.md)
- Opt-out behavior: [How-to: Opt Out with @NotIdempotent](../how-to/opt-out-with-not-idempotent.md)
- Exact semantics and schema: [Idempotency Reference](../reference/idempotency-reference.md)
