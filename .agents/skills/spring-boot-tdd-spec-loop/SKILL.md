---
name: spring-boot-tdd-spec-loop
description: Drive implementation of Java Spring Boot requirements from files in `specs/` using strict red-green-refactor TDD loops. Use when building or changing Spring Modulith + Spring Data JDBC features, especially when integration-first testing with Testcontainers and contract-first DTO shaping is required.
---

# Spring Boot TDD Spec Loop

Follow this workflow to implement requirements from `specs/*.md` in deliberate, reviewable increments.

## 1. Clarify the Spec Before Coding

1. Read the target spec file and identify ambiguities, missing edge cases, and unclear contracts.
2. Ask concise clarification questions before implementation when behavior is underspecified.
3. Break the spec into piecemeal work items that can be completed in one red-green-refactor cycle each.
4. Order work items from foundational contracts to dependent behavior.

Use this work item template:

```md
- Work item: <short name>
  - Scope: <single behavior slice>
  - Test(s): <integration/unit tests to add first>
  - Production touchpoints: <service/repository/module boundaries>
```

Before you start coding, create a git branch off of the main branch using the pre-commit hook format for naming the branch.

## 2. Enforce Red-Green-Refactor Gates

For each work item, execute exactly:

1. Red
2. Green
3. Refactor

Do not batch multiple work items into one loop.

### Red Rules

1. Write failing tests first.
2. Prefer integration tests with Testcontainers and real Spring wiring.
3. Add only minimal method stubs, DTOs, and signatures required for tests to compile.
4. Keep business logic incomplete so tests fail for the right reason.
5. Run tests and stop at the failing result.
6. Pause and request user review of the new contract surface (especially DTOs) before starting Green.

### Green Rules

1. Implement the smallest production change that makes the failing tests pass.
2. Avoid unnecessary abstractions.
3. Re-run the relevant tests after each change.

### Refactor Rules

1. Improve naming, duplication, and structure without changing behavior.
2. Keep tests green throughout refactor.
3. Keep module boundaries explicit and compatible with Spring Modulith design.

## 3. Testing Architecture Rules

1. Use Testcontainers for integration testing.
2. Keep integration tests on one abstract base class so a shared Spring Boot test context is reused across integration test runs.
3. Centralize container setup, Spring bootstrapping, and common fixtures in that base class.
4. Use production services/repositories for straightforward setup where practical.
5. Create dedicated test support helpers only when setup becomes repetitive or noisy.
6. Use mocks sparingly; prefer real collaborators unless isolation is essential for the specific test intent.

## 4. Spring Stack Conventions

1. Target Java + Spring Boot + Spring Modulith + Spring Data JDBC.
2. Do not introduce JPA patterns or annotations.
3. Represent table-mapped data objects as `*Entity.java`.
4. For service methods with more than a small handful of values, use `*Request.java` and `*Response.java` instead of long parameter lists.
5. Keep repository and service responsibilities clear: persistence in repositories, orchestration/rules in services.

## 5. Execution Pattern in a Live Task

1. State which spec file and work item are being addressed.
2. Complete Red and show failing test result.
3. Pause for user feedback on contracts/DTOs before Green.
4. Complete Green and Refactor after confirmation.
5. Repeat for the next work item until the spec slice is done.

Prefer small, auditable commits of progress over large mixed changes.

## 6. Final Review and Commit

1. After all work items for the spec are complete, review the full change set for correctness, clarity, and adherence to conventions.
2. Create `implementation/<feature_name>.md` documentation summarizing the implementation and any important design decisions, trade-offs, future considerations & nice-to-haves, or changes from the spec.
3. Run spotlessApply to ensure code style is consistent.
4. Run gradle check to ensure all tests pass and code quality checks are satisfied.
5. Create a commit in the conventional commit style. The body should provide a concise summary of the changes made.