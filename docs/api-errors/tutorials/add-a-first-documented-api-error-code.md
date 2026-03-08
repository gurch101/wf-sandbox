# Tutorial: Add a First Documented ApiErrorCode to an Endpoint

## Outcome

Define a typed error enum, throw it from service logic, and expose it in OpenAPI docs for a
controller endpoint.

## Prerequisites

- Existing controller and service
- Error enum implementing `ApiErrorCode`
- Access to `ValidationErrorException`

## Step 1: Define or extend an ApiErrorCode enum

```java
public enum WidgetErrorCode implements ApiErrorCode {
  WIDGET_NAME_ALREADY_EXISTS("name", "widget name already exists", HttpStatus.BAD_REQUEST);

  private final String fieldName;
  private final String message;
  private final HttpStatus status;

  // constructor + ApiErrorCode methods
}
```

## Step 2: Throw ValidationErrorException from service logic

```java
if (nameExists(command.getName())) {
  throw ValidationErrorException.of(WidgetErrorCode.WIDGET_NAME_ALREADY_EXISTS);
}
```

## Step 3: Document enum on endpoint with @ApiErrorEnum

```java
@PostMapping("/api/widgets")
@ApiErrorEnum({WidgetErrorCode.class})
@ResponseStatus(HttpStatus.CREATED)
public CreateResponse create(@Valid @RequestBody WidgetCreateRequest request) {
  ...
}
```

## Step 4: Confirm middleware response shape

When thrown, `GlobalExceptionHandler` returns `application/problem+json` with `errors` details.

## Verify

- Runtime response contains `errors[0].code = "WIDGET_NAME_ALREADY_EXISTS"`.
- OpenAPI response for matching HTTP status includes documented validation/business errors.
- OpenAPI extension `x-error-codes` contains enum entries.

## Next Steps

- For middleware patterns, see [Handle Exceptions Through Global Middleware](../how-to/handle-exceptions-through-global-middleware.md).
- For full contracts and components, see [API Error Handling Reference](../reference/api-error-handling-reference.md).
