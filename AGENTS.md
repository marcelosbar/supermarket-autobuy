# AI Agent Guidelines: Supermarket Auto-Buy

## 1. Project Directory Layout
* `src/main/java/com/autobuy/`
  * `cli/`: CommandLineRunner interface loops.
  * `config/`: Spring Configuration beans.
  * `driver/`: `SupermarketDriver` and scraper implementations (e.g. Playwright).
  * `model/`: JPA Entities and core data classes.
  * `provider/`: credential and shopping list loading services.
  * `repository/`: Spring Data JPA interfaces.
  * `service/`: Core transactional services (backup, logging).
* `secrets.properties`: (Excluded from git) local key-value store for passwords.

## 2. CLI Commands
* **Build & Package:** `.\mvnw.cmd clean package`
* **Run Tests:** `.\mvnw.cmd test` (enforces 80% instruction coverage gate via JaCoCo)
* **Run App:** `.\mvnw.cmd spring-boot:run`
* **Custom Execution:** `.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--list=shopping-list.json --supermarket=CONTINENTE"`
* **Auto-format Code:** `.\mvnw.cmd spotless:apply` (automatically applies Eclipse JDT 4-space indent style)

## 3. Coding Guidelines & Standards
* **JDK Version:** Targets Java 25.
* **SOLID Compliance:** Construct objects explicitly (constructor injection). Inject interfaces (`CredentialProvider`, `SupermarketDriver`), not implementations.
* **SOLID Exception Rule:** If a class must deviate from SOLID, write an explicit Javadoc explanation containing `<b>SOLID Exception:</b> [Reason]`.
* **Database & Locks:** Keep H2 database file (`db.mv.db`) outside OneDrive. Run backup snapshot exports only on shutdown using `DatabaseBackupService`.
