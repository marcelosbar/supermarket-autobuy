# Original User Request

## Initial Request — 2026-06-27T21:32:01+01:00

Refactor the backend codebase of the Supermarket Auto-Buy application, tackling only the Wave 1 issues of the architecture review milestone. Each task is mapped to a specific open GitHub issue below. When implementing, the agents must refer to these issue numbers (e.g., "fixes #1" or "related to #2") in their pull requests, commits, and branch names.

Working directory: C:\Users\marce\.gemini\antigravity\worktrees\supermarket-autobuy\review-application-architecture-standards
Integrity mode: demo

## Requirements (Wave 1 Tasks)

### R1. Interface-Compliant Credential Saving (DIP Fix) — Fixes #1
- **GitHub Issue:** [#1](https://github.com/marcelosbar/supermarket-autobuy/issues/1)
- **Details:** Add a default `saveCredentials(String, String, String)` method to the `CredentialProvider` interface (throwing `UnsupportedOperationException` by default) and override it in `PropertiesCredentialProvider`.

### R2. Extract ProductService — Fixes #2
- **GitHub Issue:** [#2](https://github.com/marcelosbar/supermarket-autobuy/issues/2)
- **Details:** Create a new `ProductService` class in the `service/` package encapsulating all product and mapping business logic (save mapping, find mapping, delete mapping, find-or-create product). Mutating methods must be annotated with `@Transactional`.

### R3. Extract PriceHistoryService — Fixes #3
- **GitHub Issue:** [#3](https://github.com/marcelosbar/supermarket-autobuy/issues/3)
- **Details:** Create a new `PriceHistoryService` class in the `service/` package encapsulating price logging. The `logPrice()` method must be `@Transactional`.

### R4. Extract DTOs to web/dto/ package — Fixes #4
- **GitHub Issue:** [#4](https://github.com/marcelosbar/supermarket-autobuy/issues/4)
- **Details:** Extract `CredentialsRequest`, `RunRequest`, `ResolveRequest`, and `AutoBuyStatusResponse` from inner classes in the controller/web service into standalone Java records in the `web/dto/` package.

### R5. PriceHistory LAZY Fetching & ObjectMapper Injection — Fixes #8
- **GitHub Issue:** [#8](https://github.com/marcelosbar/supermarket-autobuy/issues/8)
- **Details:** Change `PriceHistory.product` fetch type to `FetchType.LAZY`. Inject the Spring-provided `ObjectMapper` bean into `JsonShoppingListProvider` instead of instantiating it manually.

### R6. Replace MemoryAppender with logback-spring.xml — Fixes #9
- **GitHub Issue:** [#9](https://github.com/marcelosbar/supermarket-autobuy/issues/9)
- **Details:** Remove the static initializer block in `MemoryAppender` and replace it with declarative configuration in a new `logback-spring.xml` file. Use a bounded `ConcurrentLinkedDeque` instead of a `CopyOnWriteArrayList` in `MemoryAppender`.

### R7. Custom Exception Hierarchy & Global Exception Handler — Fixes #11
- **GitHub Issue:** [#11](https://github.com/marcelosbar/supermarket-autobuy/issues/11)
- **Details:** Create a custom unchecked exception hierarchy (`AutoBuyException` as base, and sub-exceptions `DriverException`, `CredentialException`, `ShoppingListException`). Create a `@ControllerAdvice` (`GlobalExceptionHandler`) to catch exceptions and return structured JSON responses.

### R8. Add Flyway for Database Migrations — Fixes #12
- **GitHub Issue:** [#12](https://github.com/marcelosbar/supermarket-autobuy/issues/12)
- **Details:** Add `flyway-core` to `pom.xml`. Change `ddl-auto` to `validate` in configuration. Add `V1__baseline_schema.sql` to resources capturing the current database schema to support safe baselining on startup.

## Verification Resources

- Existing test suite (run via `.\mvnw.cmd test`)
- Spotless formatter (run via `.\mvnw.cmd spotless:check`)

## Acceptance Criteria

### Compilability & Formatting
- [ ] Code builds successfully with no compiler errors or warnings.
- [ ] Code formatting complies with Spotless checks (`.\mvnw.cmd spotless:check` passes).

### Test Verification
- [ ] All existing unit and integration tests pass successfully (`.\mvnw.cmd clean test` passes).
- [ ] New unit tests are added verifying the behavior of `ProductService` and `PriceHistoryService`.
- [ ] A new integration test checks that `GlobalExceptionHandler` converts exceptions to the proper JSON format.

### DB & Concurrency Verification
- [ ] Flyway runs the baseline migration successfully on startup without schema conflicts or data loss.
- [ ] No `LazyInitializationException` is thrown when lazy loading PriceHistory relationships.
- [ ] All code modifications are correctly associated with their corresponding GitHub issue numbers in commits and PR titles.
