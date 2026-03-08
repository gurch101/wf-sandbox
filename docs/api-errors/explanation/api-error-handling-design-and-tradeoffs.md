# Explanation: API Error Handling Design and Tradeoffs

## Context

The app uses centralized middleware-based error mapping to keep endpoint code focused on business
logic while maintaining consistent client-facing error payloads.

## Design

- Runtime error mapping is centralized in `GlobalExceptionHandler`.
- Business/validation error semantics are modeled through `ApiErrorCode` enums.
- Service logic throws `ValidationErrorException` with typed codes.
- OpenAPI docs are enriched via `@ApiErrorEnum` + `OpenApiConfig` customizer.

## Why This Approach

- Single place to evolve response format (`ProblemDetail`-based).
- Stable machine-readable codes for client integration.
- Low documentation drift when controllers explicitly declare `@ApiErrorEnum`.

## Tradeoffs

Pros:

- Consistent response structure across modules.
- Strong alignment between runtime behavior and OpenAPI documentation.
- Easier client-side error handling via stable codes.

Cons:

- Requires discipline to keep `@ApiErrorEnum` annotations in sync with thrown codes.
- Grouped status constraint in `ValidationErrorException` can require multiple throws/paths.
- Error detail text still needs careful curation for user-facing quality.

## Developer Guidance

Treat `ApiErrorCode` enums as API contract artifacts, not internal implementation details. Changes
to codes/messages/statuses should be reviewed like endpoint contract changes.

## Related Docs

- Implementation steps: [How-to: Document Endpoint Errors with ApiErrorCode and @ApiErrorEnum](../how-to/document-endpoint-errors-with-api-error-code.md)
- Middleware usage: [How-to: Handle Exceptions Through Global Middleware](../how-to/handle-exceptions-through-global-middleware.md)
- Exact contract details: [API Error Handling Reference](../reference/api-error-handling-reference.md)
