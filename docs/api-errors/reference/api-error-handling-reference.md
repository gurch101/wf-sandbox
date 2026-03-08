# Reference: API Error Handling

## Core Types

- `ApiErrorCode` (interface): typed error contract for runtime + docs
- `ApiErrorEnum` (annotation): declares error-code enums for OpenAPI documentation
- `ValidationErrorException`: exception carrying one or more validation/business errors
- `GlobalExceptionHandler`: centralized middleware (`@RestControllerAdvice`)
- `OpenApiConfig.apiErrorEnumCustomizer`: transforms `@ApiErrorEnum` into documented responses

## ApiErrorCode Contract

Methods:

- `fieldName()` -> affected field or domain area
- `code()` -> stable machine-readable code (defaults to enum constant name)
- `message()` -> human-readable message
- `status()` -> HTTP status

## ValidationErrorException Factory Methods

- `ValidationErrorException.of(ApiErrorCode... errorCodes)`
- `ValidationErrorException.from(List<? extends ApiErrorCode> errorCodes)`
- `ValidationErrorException.of(HttpStatus status, List<ValidationError> errors)`

Constraint:

- All `ApiErrorCode` entries in one exception must share the same `HttpStatus`.

## Middleware-Mapped Exception Categories

From `GlobalExceptionHandler`:

- `ValidationErrorException` -> validation problem detail with `errors`
- `MethodArgumentNotValidException` -> validation problem detail with `errors`
- `HttpMessageNotReadableException` -> validation problem detail with `errors`
- `NotFoundException` -> `404`
- `IdempotencyConflictException` -> `409`
- `MissingIdempotencyKeyException` -> `400`
- `PayloadTooLargeException` / `MaxUploadSizeExceededException` -> `413`
- `OptimisticLockingFailureException` -> `409`
- `IllegalArgumentException` -> `400`

## OpenAPI Documentation Behavior

`@ApiErrorEnum` entries are grouped by `status()` and applied as responses:

- `application/problem+json` content schema
- textual summary in response description
- `x-error-codes` extension with details:
  - `fieldName`
  - `code`
  - `message`

## Problem Detail Shape (validation/business)

Typical fields:

- `type`
- `title`
- `status`
- `detail`
- `instance`
- `errors` (array of `ValidationError`)
