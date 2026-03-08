# Explanation: Persistence Auditing Design and Tradeoffs

## Context

The app standardizes entity auditing through shared base classes and a single Spring Data JDBC
configuration. This avoids repeated annotation and wiring logic in each entity.

## Design

- Shared model inheritance:
  - `BaseEntity` for creation audit fields
  - `MutableEntity` for update audit fields + optimistic locking
- Global auditing enablement through `PersistenceConfig`
- Auditor identity resolved from `CurrentUserProvider`
- Audit timestamps sourced from UTC clock provider

## Why This Approach

- Enforces consistent persistence metadata across modules
- Reduces boilerplate in each entity
- Keeps cross-cutting behavior centralized and testable

## Tradeoffs

Pros:

- Uniform entity contracts for created/updated metadata
- Predictable version-based concurrency control for mutable aggregates
- Minimal per-entity setup

Cons:

- Relies on runtime security context for auditor ids
- Null auditor values can clash with strict DB constraints
- Inheritance-based model constrains alternate auditing strategies per entity

## Guidance

Treat base-class choice (`BaseEntity` vs `MutableEntity`) as part of domain lifecycle design, not a
stylistic preference.

- If rows are updated, prefer `MutableEntity`.
- If rows are append-only/event-like, prefer `BaseEntity`.

## Related Docs

- Selection checklist: [Choose Between BaseEntity and MutableEntity](../how-to/choose-between-baseentity-and-mutableentity.md)
- Runtime population steps: [Ensure Audit Fields Populate Correctly](../how-to/ensure-audit-fields-populate-correctly.md)
- Exact contracts: [Persistence Reference](../reference/persistence-reference.md)
