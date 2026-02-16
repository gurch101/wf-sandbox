# wf-sandbox Project Learnings

## Architectural Decisions

### 1. Spring Modulith & Encapsulation
*   **Encapsulation Strategy**: Each module (e.g., `requests`) should have a public top-level package and an `internal` sub-package. 
    *   **Public**: Interfaces (`RequestApi`), Public Enums (`RequestStatus`), and Shared DTOs (`RequestResponse`, `RequestSearchCriteria`).
    *   **Internal**: Implementation details like Services (`DefaultRequestService`), Entities (`RequestEntity`), Repositories, and Controller-specific DTOs.
*   **Enforcement**: Spring Modulith's `ApplicationModules.verify()` ensures that no other module can access the `internal` packages, preserving strict architectural boundaries.

### 2. Standardized API Design
*   **Error Handling**: The project follows **RFC 7807 (Problem Details for HTTP APIs)**. Custom handlers provide structured `errors` lists for validation failures, providing field names, error codes, and human-readable reasons.
*   **Optimistic Locking**: Implemented via `@Version` on entities. Update endpoints **must** require the current version from the client to prevent "lost updates," returning a `409 Conflict` on mismatch.
*   **Auditing**: Enabled `@EnableJdbcAuditing` to automatically manage `createdAt` and `updatedAt` timestamps on persistence operations.

### 3. Dynamic SQL & Search
*   **SQLQueryBuilder**: A custom fluent API for building complex SQL queries.
    *   **Named Parameters**: Ensures safety against SQL injection.
    *   **Null-Skipping**: Automatically omits `WHERE` clauses for null filter values, simplifying service logic.
    *   **Operator Support**: Specifically refined `IN` operator support to handle collection expansion correctly.

### 4. Automated Documentation & Quality
*   **OpenAPI/Swagger**: Integrated `springdoc-openapi` for automatic API documentation available at `/swagger-ui.html`.
*   **Documentation Enforcement**: An **ArchUnit** test (`OpenApiDocumentationTest`) ensures that all DTOs used in controllers are fully documented with OpenAPI `@Schema` annotations.
*   **Strict Linting**: Checkstyle and Spotless are configured to enforce consistent formatting. Checkstyle was specifically adjusted to be compatible with Spotless regarding empty types and whitespace.

## Tech Stack
*   **Runtime**: Java 25 (OpenJDK)
*   **Framework**: Spring Boot 3.5.x
*   **Persistence**: Spring Data JDBC, PostgreSQL, Flyway
*   **Quality**: ArchUnit, Checkstyle, PMD, SpotBugs, Spotless
*   **Testing**: JUnit 5, Testcontainers, MockMvc

## Useful Commands
*   `./gradlew check`: Run all tests, linting, and static analysis.
*   `./gradlew spotlessApply`: Automatically fix formatting violations.
*   `./gradlew test --tests com.gurch.sandbox.ModuleStructureTest`: Verify architectural boundaries.
