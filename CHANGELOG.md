# Changelog

Generated from `start..HEAD` on 2026-02-19.

## Features

- **users**: add admin crud for users and tenants (ec5d9e6)
- update commit-msg hook to allow uppercase in description (a50eca6)
- **persistence**: add audited base entities (301af98)
- implement request types (758607c)
- **forms**: stream files, cap uploads, and simplify metadata (69d445e)
- **forms**: add filesystem-backed form file module (3539f1b)
- **requests**: integrate fluxnova workflow and task projection (b5f4160)
- **requests**: add enum-based validation errors and openapi docs (2fc17c5)
- **requests**: improve error handling (3b46d9e)
- **query**: allow page to be null (b7a75d4)
- **git**: add branch protection hooks (d3a78d3)
- **idempotency**: implement global idempotency mechanism (5fa273a)
- **requests**: implement request lifecycle with search and crud (d8ef594)
- **query**: support in operator with named parameters (b1f557e)
- **query**: add fluent sql query builder (b8aac65)

## Bug Fixes

- **test**: disable job-execution to prevent shutdown hang (b758e3d)
- dev docker postgres volume path (711ccb6)

## Refactoring

- **query**: improve page and enum handling (f4bfe4b)

## Documentation

- add javadoc requirements (fb9d5e8)
- add spec writer/tdd implementer agents (6e5ef73)

## Build

- **errorprone**: treat warnings as errors (#14) (8bb67d0)

## CI

- set gradle properties (8cd658f)

## Tests

- **users**: cover remaining service constraint violations (4fade08)
- **users**: resolve pr review comments (1b9d9e5)

## Chores

- **skills**: add fetch-pr-review-comments skill (b63dd6c)

