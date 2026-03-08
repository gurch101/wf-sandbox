# Tutorial: Implement a First Idempotent Endpoint

## Outcome

Expose a mutating endpoint that replays the same response for repeated requests with the same
`Idempotency-Key` and payload.

## Prerequisites

- Spring controller method using `@PostMapping`, `@PutMapping`, or `@DeleteMapping`
- Request body DTO marked with `@RequestBody`
- Endpoint not annotated with `@NotIdempotent`

## Step 1: Add endpoint normally

```java
@PostMapping("/api/widgets")
@ResponseStatus(HttpStatus.CREATED)
public CreateResponse create(@Valid @RequestBody WidgetCreateRequest request) {
  return new CreateResponse(widgetApi.create(request));
}
```

No direct idempotency wiring is needed in this method.

## Step 2: Call endpoint with Idempotency-Key header

Example request headers:

```text
Idempotency-Key: 84c6f0ab-b37c-4d1e-9c17-44a9a869f9c7
Content-Type: application/json
```

## Step 3: Repeat with same key and same payload

The second request returns the stored response instead of executing business logic again.

## Verify

- First call succeeds and persists a record in `api_idempotency_records`.
- Second identical call returns same status/body.
- Same key with different payload returns HTTP `409` conflict.

## Next Steps

- For endpoint-level checklist, see [Make a Mutating Endpoint Idempotent](../how-to/make-a-mutating-endpoint-idempotent.md).
- For exact runtime behavior and schema, see [Idempotency Reference](../reference/idempotency-reference.md).
