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
* **Run Tests:** `.\mvnw.cmd test 2>$null | Select-String "Results:", "Tests run:", "BUILD", "ERROR", "Failed", "violations"` (enforces 80% instruction coverage gate via JaCoCo). Use piping to filter verbose output and save LLM token context. During the active coding phase, always run targeted tests (e.g. `.\mvnw.cmd test -Dtest=TestClassName | Select-String "Results:", "Tests run:", "BUILD", "ERROR", "Failed", "violations"`) and leave the whole test suite execution for the final verification.
* **Run App:** `.\mvnw.cmd spring-boot:run` (Starts the Web UI and automatically executes Flyway baseline/migrations)
* **Custom Execution:** `.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--list=shopping-list.json --supermarket=CONTINENTE"`
* **Auto-format Code:** `.\mvnw.cmd spotless:apply` (automatically applies Eclipse JDT 4-space indent style)

## 3. Coding Guidelines & Standards
* **JDK Version:** Targets Java 25.
* **SOLID Compliance:** Construct objects explicitly (constructor injection). Inject interfaces (`CredentialProvider`, `SupermarketDriver`), not implementations.
* **SOLID Exception Rule:** If a class must deviate from SOLID, write an explicit Javadoc explanation containing `<b>SOLID Exception:</b> [Reason]`.
* **Database & Locks:** Keep H2 database file (`db.mv.db`) outside OneDrive. Run backup snapshot exports only on shutdown using `DatabaseBackupService`.
