# AI Agent Guidelines: Supermarket Auto-Buy

## 1. Project Directory Layout
* `src/main/java/com/autobuy/`
  * `config/`: Spring Configuration beans (contains `AppConfig`).
  * `driver/`: `SupermarketDriver` and scraper implementations (e.g. Playwright).
  * `exception/`: Custom unchecked exception hierarchy (`AutoBuyException`, etc.).
  * `model/`: JPA Entities and core data classes.
  * `provider/`: Credential provider and shopping list loading services.
  * `repository/`: Spring Data JPA interfaces.
  * `service/`: Core transactional services (`DatabaseBackupService`, `ProductService`, `PriceHistoryService`).
  * `web/`: Controller and web services.
    * `dto/`: Request/response DTO records.
* `secrets.properties`: (Excluded from git) local key-value store for passwords.

## 2. CLI Commands
* **Build & Package:** `.\mvnw.cmd clean package`
* **Run Tests:** `.\mvnw.cmd test 2>$null | Select-String "Results:", "Tests run:", "BUILD", "ERROR", "Failed", "violations"`. During active development, add `-Dtest=TestClassName` for targeted test execution. Always use piping to filter verbose output and save LLM token context. Run the whole test suite for final verification only.
* **Run App:** `.\mvnw.cmd spring-boot:run` (Starts the Web UI and automatically executes Flyway baseline/migrations)
* **Auto-format Code:** `.\mvnw.cmd spotless:apply` (automatically applies Eclipse JDT 4-space indent style)

## 3. Coding Guidelines & Standards
* **JDK Version:** Targets Java 25.
* **SOLID Compliance:** Construct objects explicitly (constructor injection). Inject interfaces (`CredentialProvider`, `SupermarketDriver`), not implementations.
* **SOLID Exception Rule:** If a class must deviate from SOLID, write an explicit Javadoc explanation containing `<b>SOLID Exception:</b> [Reason]`.
* **Database & Locks:** Keep H2 database file (`db.mv.db`) outside OneDrive. Run backup snapshot exports only on shutdown using `DatabaseBackupService`.
* **Testing Pyramid & Separation:** Maintain a healthy testing pyramid (preferring unit tests over integration tests). Keep pure unit tests named with a `*Test.java` suffix (run via `.\mvnw.cmd test` using Surefire), and integration/slice tests named with an `*IT.java` suffix (run via `.\mvnw.cmd verify` using Failsafe). Code coverage (minimum 80% instruction coverage) is evaluated globally during the `verify` phase.

## 4. Git Commit Guidelines
* **Conventional Commits:** Always write commit messages following the Conventional Commits specification (e.g., `feat: ...`, `fix: ...`, `refactor: ...`, `docs: ...`).

## 5. Design Principles
* **Minimize Interruptions:** Any action that requires user input should be deferred and batched. Front-load decisions; back-load automation.
  * **Pre-run:** resolve unknowns (unmapped items) up front before the automated loop begins.
  * **Post-run:** resolve exceptions (unavailable products) at the end in batch.
  * **Mid-run:** interruptions should only happen when there is genuinely no other option.

