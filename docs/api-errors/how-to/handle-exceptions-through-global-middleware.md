# How-to: Handle Exceptions Through Global Middleware

## Goal

Implement consistent API error responses by routing exceptions through `GlobalExceptionHandler`.

## Prerequisites

- `@RestControllerAdvice` handler class (`GlobalExceptionHandler`)
- Domain/service exceptions (for example `ValidationErrorException`, `NotFoundException`)

## Procedure

1. Throw domain-specific exceptions from service/controller code.
2. Add `@ExceptionHandler` mappings in `GlobalExceptionHandler` for custom exceptions.
3. Return `ProblemDetail` with consistent status/title/detail and optional `errors` property.
4. Keep validation mapping centralized in middleware (method-arg validation + JSON parse errors).

## Standard Shapes

- Validation/business errors: `application/problem+json` + `errors` array
- Not found: HTTP `404` `ProblemDetail`
- Idempotency conflicts: HTTP `409` `ProblemDetail`
- Payload too large: HTTP `413` `ProblemDetail`

## Example Handler Method

```java
@ExceptionHandler(NotFoundException.class)
public ProblemDetail handleNotFoundException(NotFoundException e) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
}
```

## Validation

- Ensure each custom exception has one clear mapping path.
- Ensure response statuses are stable and aligned with endpoint contracts.
- Ensure JSON body parsing errors map to validation-style `errors` entries.

## Troubleshooting

- Error bypasses middleware:
  - Confirm exception type is handled or extends a handled category.
- Inconsistent error payloads:
  - Keep ad-hoc controller try/catch blocks out of endpoints; throw and let middleware map.
