# <Feature Name> Specification

## 1. Summary

- Problem:
- Business objective:
- In scope:
- Out of scope:

## 2. Confirmed Decisions

- Decision:
- Decision:

## 3. Blocked Items (Must Resolve Before Build)

- Item:
- Why blocking:
- Question for stakeholder:

## 4. Feature Breakdown

### 4.1 <Feature Slice Name>

- Description:
- API impact:
- Data impact:
- Workflow impact:
- Security impact:
- Observability impact:

## 5. User Stories

### US-001: <Short Name>

- As a `<role>`
- I want `<capability>`
- So that `<outcome>`
- Priority: `<P0/P1/P2>`

### US-002: <Short Name>

- As a `<role>`
- I want `<capability>`
- So that `<outcome>`
- Priority: `<P0/P1/P2>`

## 6. API Specification

### Endpoint: `<METHOD> <path>`

- Purpose:
- Auth:
- Request schema:
- Response schema:
- Errors:
- Idempotency:

## 7. Data Model and Persistence

- Tables/entities affected:
- New columns/constraints/indexes:
- Transaction boundaries:
- Concurrency strategy:

## 8. Camunda 7 Workflow Specification

- Process key/name:
- Trigger:
- Variables:
- Service tasks and retry strategy:
- Incidents/escalations:
- Compensation/cancellation behavior:

## 9. Non-Functional Requirements

- Performance targets:
- Reliability/SLA expectations:
- Security controls:
- Observability (logs/metrics/traces):

## 10. Acceptance Tests (Integration)

### AT-001: <Scenario>

- Covers user story:
- Preconditions:
- Test steps:
- Expected API result:
- Expected DB state:
- Expected Camunda state:

### AT-002: <Scenario>

- Covers user story:
- Preconditions:
- Test steps:
- Expected API result:
- Expected DB state:
- Expected Camunda state:

## 11. Edge Cases and Failure Scenarios

- Case:
- Expected behavior:

## 12. Adjacent Feature Recommendations

- Recommendation:
- Reason:
- Include now? `<yes/no>`
