# Spring/JDBC/Postgres/Camunda Spec Checklist

Use this checklist while expanding the feature snippet into a full specification.

## 1. API Contract

- Define endpoints, methods, request/response schemas, and status codes.
- Define validation rules and error payload format.
- Define idempotency requirements for create/update actions.
- Define pagination/filter/sort behavior for list endpoints.
- Define authn/authz requirements per endpoint.

## 2. Data and Persistence

- Define table changes, keys, constraints, and index strategy.
- Define repository responsibilities and query patterns.
- Define transaction boundaries across writes and process starts.
- Define optimistic/pessimistic locking rules where race conditions are possible.
- Define retention/archive requirements for old records.

## 3. Workflow (Camunda 7)

- Define BPMN process name and trigger conditions.
- Define process variables, types, and lifecycle.
- Define service task behavior, retries, and incident handling.
- Define timeout/escalation paths and compensation behavior.
- Define how process state is queried or exposed via API.

## 4. Performance and Robustness

- Define p95/p99 latency and throughput targets.
- Define expected data volume and growth assumptions.
- Define failure modes (DB outage, external dependency timeout, worker crash).
- Define retry/backoff/circuit-breaker expectations where applicable.
- Define graceful degradation behavior.

## 5. Observability and Operations

- Define structured logging requirements and key fields (request id, process id, entity id).
- Define metrics for success/failure/retry/latency.
- Define tracing expectations across API call -> DB -> workflow.
- Define alert thresholds for incidents and SLO violations.

## 6. Security and Compliance

- Define data classification and sensitive fields.
- Define encryption requirements at rest/in transit.
- Define audit trail events and retention period.
- Define least-privilege DB and workflow access constraints.

## 7. Testability

- Define integration test setup requirements (containers, seeded data, workflow engine).
- Define happy path, validation errors, permission errors, and concurrency cases.
- Define workflow success/failure/retry paths as executable tests.
- Define DB assertions after each relevant API/workflow step.
