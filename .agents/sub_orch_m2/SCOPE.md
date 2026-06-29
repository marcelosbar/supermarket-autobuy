# Scope: Milestone 2 (Web, Exception & Log Refactoring)

## Architecture
- Extract request/response data transfer objects (DTOs) from the web controller into the standalone package `com.autobuy.web.dto`.
- Replace custom log buffering (`MemoryAppender` static initialization) with declarative logging in `logback-spring.xml`.
- Update `MemoryAppender` to use a bounded `ConcurrentLinkedDeque` (e.g. limit to 100 or 1000 items) to prevent memory leak, instead of `CopyOnWriteArrayList`.
- Implement a custom runtime exception hierarchy for the application and a `@ControllerAdvice` global exception handler returning structured JSON error details.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M2.1 | Extract DTOs (R4) | Extract `CredentialsRequest`, `RunRequest`, `ResolveRequest`, and `AutoBuyStatusResponse` to `com.autobuy.web.dto`. | None | PLANNED |
| M2.2 | Logging Refactor (R6) | Move `MemoryAppender` config to `logback-spring.xml`, rewrite using bounded `ConcurrentLinkedDeque`. | None | PLANNED |
| M2.3 | Exceptions & Handler (R7) | Custom unchecked exceptions (`AutoBuyException`, etc.), `GlobalExceptionHandler`, and integration tests. | M2.1, M2.2 | PLANNED |

## Interface Contracts
### DTO Records (Package `com.autobuy.web.dto`)
- `record CredentialsRequest(String supermarket, String username, String password)`
- `record RunRequest(String supermarket, String listPath, boolean resolve)`
- `record ResolveRequest(Long id, String action, String value)`
- `record AutoBuyStatusResponse(String status, String currentProduct, String message, List<String> logs)`

### Exception Hierarchy
- Base: `AutoBuyException` (extends `RuntimeException`)
- Subclasses: `DriverException`, `CredentialException`, `ShoppingListException`

### Global Exception Handler
- Annotations: `@ControllerAdvice`
- Target JSON structure for errors: Must return JSON containing error details (e.g. status code, timestamp, message, path, etc.) when exceptions are thrown by controllers.
