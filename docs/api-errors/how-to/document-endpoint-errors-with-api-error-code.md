# How-to: Document Endpoint Errors with ApiErrorCode and @ApiErrorEnum

## Goal

Make endpoint error behavior explicit and machine-readable in OpenAPI using `ApiErrorCode` enums.

## Prerequisites

- Error enum implementing `ApiErrorCode`
- Controller method/class where errors can occur
- `OpenApiConfig.apiErrorEnumCustomizer` enabled

## Procedure

1. Model errors as enum constants implementing `ApiErrorCode`.
2. Ensure each enum constant has:
   - `fieldName()`
   - stable code (default enum name)
   - `message()`
   - `status()`
3. Attach `@ApiErrorEnum({YourErrorCode.class})` at method or controller level.
4. Throw `ValidationErrorException.of(...)` with enum values from service logic.
5. Regenerate/check OpenAPI and verify status responses + `x-error-codes` extension.

## Annotation Placement Rules

- Use method-level `@ApiErrorEnum` for endpoint-specific errors.
- Use class-level `@ApiErrorEnum` for shared module-wide errors.
- Both can coexist; OpenAPI customizer merges and de-duplicates enum types.

## What OpenApiConfig Adds

For each documented status:

- response description suffix with summarized codes/messages
- `application/problem+json` schema containing `errors`
- `x-error-codes` extension list (`fieldName`, `code`, `message`)

## Validation

- Every thrown `ApiErrorCode` for an endpoint is represented in `@ApiErrorEnum`.
- HTTP statuses in enum match expected runtime status behavior.
- Endpoint documentation stays in sync when new error constants are added.
