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
* **Build and package** the project using: `.\mvnw.cmd clean package`.
* **Run tests** during active development using: `.\mvnw.cmd test 2>$null | Select-String "Results:", "Tests run:", "BUILD", "ERROR", "Failed", "violations"`.
* **Add target parameter** `-Dtest=TestClassName` during active development to run a single test.
* **Always use piping** when running tests. This filters verbose output and saves LLM token context.
* **Pre-push Verification**: Always run the entire verification suite (including integration tests and JaCoCo coverage checks) locally before pushing using: `.\mvnw.cmd verify 2>$null | Select-String "Results:", "Tests run:", "BUILD", "ERROR", "Failed", "violations"`. **Note:** You must commit your changes locally before running this command so that the `diff-coverage` plugin can detect the modified files and execute the delta coverage checks. Do NOT run verification for changes to documentation-only files (such as `.md`, `.txt`, `.gitignore`). Only execute verification when source code or executable logic is modified.
* **Run the application** using: `.\mvnw.cmd spring-boot:run`. This starts the Web UI and runs migrations.
* **Auto-format code** using: `.\mvnw.cmd spotless:apply`. This applies the Eclipse JDT 4-space indent style.

## 3. Coding Guidelines & Standards
* **Target Java 25** for the compiler and runtime JDK.
* **Adhere to SOLID** principles across all code changes:
  * **S - Single Responsibility:** Ensure each class has only one reason to change. Split mixed-concern classes.
  * **O - Open/Closed:** Favor extension over modification. Use strategy interfaces and composition.
  * **L - Liskov Substitution:** Ensure subtypes remain substitutable for base types. Do not weaken postconditions.
  * **I - Interface Segregation:** Keep interfaces small and focused. Do not force dependency on unused methods.
  * **D - Dependency Inversion:** Depend on abstractions, not concretions. Use constructor injection only.
* **Inject interface types** instead of concrete classes (e.g., `CredentialProvider`, `SupermarketDriver`).
* **Document SOLID exceptions** using Javadoc if deviation is necessary. Write `<b>SOLID Exception:</b> [Reason]`.
* **Store H2 database** files (`db.mv.db`) outside OneDrive to prevent lock issues.
* **Export backup snapshots** only on shutdown using `DatabaseBackupService`.
* **Maintain the testing** pyramid by preferring unit tests over integration tests.
* **Name unit tests** with `*Test.java` suffix. Run them using `.\mvnw.cmd test`.
* **Name integration tests** with `*IT.java` suffix. Run them using `.\mvnw.cmd verify`.
* **Ensure 80% instruction** coverage globally. This is evaluated during the `verify` phase.
* **Always Write/Update Unit Tests:** When writing new code or modifying existing code (including bug fixes), you must always write or update unit/integration tests that verify the changes. Do not propose implementation plans or submit code without specifying and writing the corresponding test cases. Do not rely solely on manual verification.

## 4. Git Commit Guidelines

* **Use Conventional Commits** format for all commit messages (e.g., `feat: ...`, `fix: ...`, `refactor: ...`).

## 5. Design Principles

* **Minimize user interruptions** by deferring and batching tasks that require human input.
* **Resolve unknown items** first (pre-run phase) before starting the main automated shopping loop.
* **Handle product exceptions** last (post-run phase) in a single batch.
* **Only interrupt execution** mid-run if no alternative action is possible.
