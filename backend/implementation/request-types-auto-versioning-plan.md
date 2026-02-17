# Implementation: Request Types Auto Versioning

## What was implemented
- Added request type catalog with immutable versions:
  - `request_types` table for stable type metadata.
  - `request_type_versions` table for append-only versions.
  - DB constraints enforce uniqueness of `(request_type_id, version)`, one active version per type, and unique process definition key per version.
- Extended `requests` with:
  - `request_type_key`
  - `request_type_version`
  - `payload_json` (`jsonb`)

## API changes
- Replaced old draft/submit flow with create-and-submit:
  - `POST /api/requests` accepts `requestTypeKey` + `payload`.
  - No client-provided version is used; latest active version is always resolved server-side.
- Kept details/search endpoints:
  - `GET /api/requests/{id}` returns payload + key/version/status.
  - `GET /api/requests/search` returns summary (payload omitted).
  - Search supports `requestTypeKeys` list filtering.
- Added internal request type admin endpoints:
  - `POST /api/internal/request-types`
  - `PUT /api/internal/request-types/{typeKey}`
  - Update operation retires old active version and creates/activates new version transactionally.

## Workflow changes
- Added request-type process definitions with external tasks:
  - `requestTypeV1Process`
  - `requestTypeV2Process`
- Process behavior:
  - Async validation external task (`request-async-validation`)
  - Gateway to rejection vs BOR path
  - BOR external task (`request-bor`)
- Added execution listeners to maintain business statuses:
  - `IN_PROGRESS` when processing starts
  - `REJECTED` on rejection end path
  - `COMPLETED` on successful completion end path

## Validation and payload handlers
- Added payload handler registry by `payloadHandlerId`.
- Added handlers:
  - `noop`
  - `amount-positive` (sync validation rule for positive numeric amount)
- Request submission resolves the handler from active type version and validates payload before persistence/workflow start.

## Tests
- Reworked request integration tests around the new contract and workflow behavior:
  - latest-active version resolution and pinning old requests
  - client cannot force version
  - sync validation failure behavior
  - async validation reject path
  - async pass path to BOR
  - BOR completion to terminal success
  - retries exhausted/incident keeps `IN_PROGRESS`
  - details include payload, search omits payload
  - search by list of `requestTypeKeys`
- Updated idempotency integration tests to new `POST /api/requests` contract.
- Added coverage-focused tests for:
  - `GlobalExceptionHandler`
  - `OpenApiConfig`
  - `PersistenceConfig` JSONB converters
  - `JacksonConfig`

## Notes and trade-offs
- Existing draft-specific domain (`RequestDraftErrorCode`, old draft endpoints) remains in codebase as unused legacy artifacts and can be removed in a follow-up cleanup.
- Search still supports legacy `nameContains` filtering, with request name currently set from `requestTypeKey` for compatibility.
- Two process definitions are included to satisfy unique process-key-per-version constraints in integration scenarios.
