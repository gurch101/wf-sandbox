# How-to: Opt Out with @NotIdempotent When Required

## Goal

Disable global idempotency for a specific mutating endpoint when replay storage is not desired.

## When to Use

Use `@NotIdempotent` only when replay semantics are inappropriate or impossible (for example,
streaming uploads with non-replayable bodies).

## Procedure

1. Annotate the controller method:

```java
@NotIdempotent
@PostMapping("/api/forms/upload")
public CreateResponse upload(@Valid @ModelAttribute UploadRequest request) {
  ...
}
```

2. Document why opt-out is needed in code comments or module docs.
3. Verify OpenAPI header requirement is not applied for this endpoint.

## Validation

- Endpoint can be called without `Idempotency-Key`.
- No idempotency record is created for this route.

## Risk Note

Opted-out endpoints lose automatic duplicate-suppression and replay guarantees.
