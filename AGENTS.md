# AI Agent Guidelines: Supermarket Auto-Buy

## 1. Project Directory Layout

* `src/main/java/com/autobuy/`
  * `config/`: Spring `@Configuration` beans and infrastructure setup.
  * `driver/`: `SupermarketDriver` interface and store-specific Playwright implementations.
  * `exception/`: Custom unchecked exception hierarchy (`AutoBuyException` subclasses).
  * `model/`: JPA entities, Java records (value objects), and enums.
  * `provider/`: Interfaces and implementations for external data sources (credentials, shopping lists, settings, folder selection).
  * `repository/`: Spring Data JPA interfaces.
  * `service/`: Core business services with transactional boundaries.
  * `web/`: REST controllers, `GlobalExceptionHandler`, and web infrastructure.
    * `dto/`: Request/response DTO records for the REST API.
* `secrets.properties`: (Excluded from git) local key-value store for passwords.
* **Read `package-info.java`** before adding a new class to any package. Each package contains one with detailed constraints.

## 2. CLI Commands

* **Build and package** using: `.\mvnw.cmd clean package`.
* **Run tests** using: `.\mvnw.cmd test -q` (Windows) or `./mvnw test -q` (Linux/macOS).
* **Run a single test** by adding `-Dtest=TestClassName`.
* **Pre-push verification** using: `.\mvnw.cmd verify -q` (Windows) or `./mvnw verify -q` (Linux/macOS). Commit locally first so `diff-coverage` can detect modified files. Skip verification for documentation-only changes.
* **Run the application** using: `.\mvnw.cmd spring-boot:run`. Starts the Web UI and runs migrations.
* **Auto-format code** using: `.\mvnw.cmd spotless:apply`. Applies Eclipse JDT 4-space indent style.

## 3. Layer Dependency Rules

```
web       → service, model, web/dto, exception
service   → repository, model, provider, driver (interface only), exception
provider  → model, exception
driver    → model, exception
repository→ model
model     → (nothing)
exception → (nothing)
web/dto   → model
config    → (nothing)
```

* **Controllers MUST NOT** contain business logic. Delegate to services, translate between HTTP and DTOs.
* **Services MUST NOT** depend on `web` package classes.
* **Never import** a concrete driver implementation from `service` — use the `SupermarketDriver` interface.

## 4. Interface & DI Conventions

* **Create interfaces** for `provider/` and `driver/` classes (ports to external systems). Name implementations with a `Default` prefix (e.g., `CredentialProvider` interface, `DefaultCredentialProvider` class).
* **Do not create interfaces** for `service/` classes — inject concrete `@Service` types directly.
* **Use constructor injection** exclusively. No field injection (`@Autowired` on fields).

## 5. API Conventions

* **Use typed DTO records** in `web/dto/` for all REST responses. Never return `Map<String, Object>`.
* **Let `GlobalExceptionHandler` handle errors.** Controllers must NOT catch exceptions and build error maps manually.
* **Validate request DTOs** with Bean Validation (`@NotNull`, `@NotBlank`) and `@Valid` on controller parameters.
* **Place only REST API-facing DTOs** in `web/dto/`. Internal data classes go in `model/`.

## 6. Exception Conventions

* **Extend `AutoBuyException`** for all custom exceptions (unchecked hierarchy).
* **Never throw** raw `RuntimeException`, `IllegalStateException`, or `IllegalArgumentException` from service/provider/driver code.
* **Never use** checked exceptions in public API contracts — wrap in an `AutoBuyException` subclass.
* **Never silently swallow** errors (e.g., returning empty list on parse failure) — throw and let the caller decide.

## 7. Coding Standards

* **Target Java 25** for the compiler and runtime JDK.
* **Follow SOLID principles.** Document exceptions using Javadoc: `<b>SOLID Exception:</b> [Reason]`.
* **Target max 300 lines** per class. Evaluate decomposition if exceeded.
* **Max 5 constructor dependencies** per class. Evaluate splitting responsibilities if exceeded.
* **Store H2 database** files outside OneDrive to prevent lock issues.
* **Export backup snapshots** only on shutdown using `DatabaseBackupService`.
* **JPA entities** must implement `equals()`/`hashCode()` using business keys.

## 8. Testing Conventions

* **Name unit tests** with `*Test.java` suffix. Run with `.\mvnw.cmd test`.
* **Name integration tests** with `*IT.java` suffix. Run with `.\mvnw.cmd verify`.
* **Ensure 80% instruction coverage** globally. Evaluated during the `verify` phase.
* **Name test methods** as `methodName_condition_expectedResult` (e.g., `saveMapping_duplicateEntry_throwsException`).
* **Use `@Mock` + `@InjectMocks`** with `@ExtendWith(MockitoExtension.class)` for unit tests. Avoid inline `mock()`.
* **Comment sections** with `// Arrange`, `// Act`, `// Assert` in every test method.
* **Never use** `System.out.println` in tests — use assertions or `Logger`.
* **Never use** raw reflection (`setAccessible(true)`) — use `ReflectionTestUtils` or package-private test hooks.
* **Always write/update tests** when writing or modifying code. Do not rely solely on manual verification.

## 9. Git Commit Guidelines

* **Use Conventional Commits** format (e.g., `feat: ...`, `fix: ...`, `refactor: ...`).

## 10. Design Principles

* **Minimize user interruptions** by deferring and batching tasks that require human input.
* **Resolve unknown items** first (pre-run phase) before starting the main automated shopping loop.
* **Handle product exceptions** last (post-run phase) in a single batch.
* **Only interrupt execution** mid-run if no alternative action is possible.
