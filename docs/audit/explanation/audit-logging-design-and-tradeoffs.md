# Explanation: Audit Logging Design and Tradeoffs

## Context

Audit logging is implemented as a reusable, cross-resource capability so writable domain modules
can emit consistent create/update/delete history without duplicating persistence logic.

## Design

- Public contract: `AuditLogApi`
- Internal implementation: `DefaultAuditLogService`
- Storage: normalized `audit_log_events` table with JSONB state snapshots
- Update semantics: store changed-field diffs rather than full-object duplicates

## Why This Approach

- Keeps business services simple: they call one API per mutation.
- Preserves flexibility across resource shapes using JSON snapshots.
- Centralizes actor/tenant/correlation handling.

## Tradeoffs

Pros:

- Uniform audit behavior across modules (`users`, `tenants`, `forms`, `request_types`, `requests`)
- Lower noise for update events due to diff-only writes
- Query-friendly indexing for resource and actor timelines

Cons:

- JSON snapshots are flexible but less strict than typed history tables
- Diff-based updates require careful reasoning in tests and consumers
- Correlation quality depends on upstream context propagation

## When Adding New Resources

Treat audit wiring as part of the mutation contract, not an optional extra. If a resource supports
create/update/delete in production paths, each path should emit corresponding audit events inside
its transaction.

## Related Docs

- Start here for implementation steps: [How-to: Add Audit Logging for a New Resource](../how-to/add-audit-logging-for-a-new-resource.md)
- Use API and schema facts: [Audit Logging Reference](../reference/audit-logging-reference.md)
