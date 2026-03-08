# How-to: Choose Between BaseEntity and MutableEntity

## Goal

Select the correct persistence base class for a new entity.

## Decision Rule

Use `BaseEntity<ID>` when the row is effectively append-only and does not need update audit fields
or optimistic-lock versioning.

Use `MutableEntity<ID>` when the row is updated after creation and should track last-modified
fields plus `version`.

## BaseEntity Includes

- `id`
- `createdBy` (`@CreatedBy`)
- `createdAt` (`@CreatedDate`)

## MutableEntity Adds

- `updatedBy` (`@LastModifiedBy`)
- `updatedAt` (`@LastModifiedDate`)
- `version` (`@Version`)

## Current Project Pattern

Common mutable domain entities (users/tenants/requests/forms/request-types/tasks) extend
`MutableEntity`.

Support/event record entities (audit log, request activity, idempotency records) extend
`BaseEntity`.

## Checklist

- [ ] Entity update lifecycle requires `updated*` and concurrency checks -> choose `MutableEntity`
- [ ] Insert-only/event-style rows -> choose `BaseEntity`
- [ ] Table schema matches selected base class fields
