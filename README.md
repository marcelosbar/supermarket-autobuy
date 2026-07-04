# Supermarket Auto-Buy

Supermarket Auto-Buy is a helper tool that automatically searches and adds items from your shopping list directly to your online supermarket cart. It currently supports **Continente Online**.

Instead of searching for each grocery item one-by-one on the store's website, you can enter your whole list in this app and let it automate the browser interactions for you. 

## Key Features
* 🛒 **Automatic Shopping:** Logs into your supermarket account, searches for each product on your list, and adds it to your cart.
* 🧠 **Smart Product Matching:** If the app is unsure about a product (e.g., you wrote "milk" and there are multiple brands), it displays the matches on the screen and asks you to pick the right one. The app remembers your choice for all future runs!
* 📈 **Price Tracking:** Keeps a history of product prices from each run so you can track price changes over time.
* 💾 **Easy Web Dashboard:** A simple, modern web interface to manage your shopping lists, save your credentials, and start/monitor shopping runs.

---

## Quick Start Guide

### Step 1: Install Java
This app requires **Java (JDK 25 or higher)** to run on your computer.
* **To check if you have it:** Open a terminal (like PowerShell or Command Prompt) and type `java -version`.
* **If you don't have it:** Download and install it from [Adoptium (Eclipse Temurin)](https://adoptium.net/) or your preferred Java vendor.

### Step 2: Run the App
1. Download or clone this project folder to your computer.
2. Open PowerShell or Command Prompt in the project folder.
3. Run the following command:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
4. Wait for the terminal to print that the application is running, then open your web browser and go to:
   **[http://localhost:8080](http://localhost:8080)**

### Step 3: Set Up and Shop
Once the web dashboard opens in your browser:
1. **Supermarket Credentials:** Go to the configuration section to enter your supermarket username and password. These are stored locally and securely on your own computer.
2. **Manage Your List:** Write or edit your shopping list directly on the screen.
3. **Start Shopping:** Click the run button. The app will open a browser session in the background. If it finds any items that need your approval, it will present cards for you to select the correct product. Once finished, check your supermarket account, and your cart will be filled!

---

# 🛑 Developer & Technical Documentation

> [!NOTE]
> The sections below contain technical details, architecture diagrams, build/test commands, and developer instructions for modifying the codebase.

## Advanced Configuration & Secrets
For automated execution or advanced users, credentials and database settings can be configured using a `secrets.properties` file in the root directory.

### 1. Local Secrets File
Copy `secrets-example.properties` to `secrets.properties` and fill in:
```properties
continente.username=your-email@example.com
continente.password=your-password
```

### 2. Database Backup & OneDrive Sync
By default, the database is persisted locally in `./data/db.mv.db`. On shutdown, a zipped backup is written to `./data/backups/backup_[timestamp].zip`.
To sync backups to OneDrive, configure the backup directory in your settings:
```properties
autobuy.backup-dir=C:/Users/your-username/OneDrive/SupermarketBackup
```
> [!IMPORTANT]
> **Windows Path Formatting:** Always use forward slashes (`/`) or double backslashes (`\\`) in `.properties` files (e.g. `C:/Users/...`). Single backslashes (`\`) are parsed as escape characters and will corrupt the path.

---

## Application Architecture

The application is built using a **Layered Architecture** style, ensuring clear separation of concerns, easy testing, and SOLID compliance:

```mermaid
graph TD
    UI[Web UI HTML/JS] <--> Controller[Controller: WebApiController]
    Controller --> Service[Service Layer: AutoBuyWebService, ProductService, PriceHistoryService]
    Service --> Repo[Repository Layer: JPA/H2]
    Service --> Driver[Driver Layer: Playwright]
```

1. **Controller Layer (`web/`):** Handles REST API requests, translates input parameters into DTOs (`web/dto/`), and delegates orchestration to services. Caught exceptions are processed globally by `GlobalExceptionHandler`.
2. **Service Layer:** Manages core business logic and transactional boundaries. Includes core transactional services (under the `service/` package, such as `ProductService` and `PriceHistoryService`) and the web UI orchestrator service (`com.autobuy.web.AutoBuyWebService`). Methods modifying persistent state are decorated with `@Transactional`.
3. **Repository Layer (`repository/` & `model/`):** Utilizes Spring Data JPA for H2 database access. Entity relations (such as `PriceHistory.product`) are configured with `FetchType.LAZY` for performance.
4. **Driver Layer (`driver/`):** Contains the automated scraper implementations (e.g., Playwright driving browser sessions on supermarket websites).

---

## Design Principles

### Minimize Interruptions
The auto-buy run is designed around a **front-load decisions, back-load automation** principle:

| Phase | What Happens | User Involvement |
|---|---|---|
| **Pre-run** | Unmapped items are sorted to the front of the queue. | User resolves all mapping prompts in rapid succession. |
| **Main run** | Mapped items are processed automatically. | None — fully automated. |
| **Post-run** | Exceptions (e.g. unavailable products) are batched for review. | User reviews and resolves at the end. |

---

## Database Migrations (Flyway)
The local H2 database schema is versioned and managed incrementally using **Flyway**:
* **Migration Scripts:** Located in [src/main/resources/db/migration/](src/main/resources/db/migration/).
* **Automatic Baselining:** On startup, Flyway checks the database state. If the database is not empty, it applies a baseline (Version 0) to avoid re-running legacy creation scripts and prevent conflicts.
* **Incremental Migrations:** Any new migration scripts are automatically applied in sequence during application startup.
* **JPA Validation:** Hibernate's DDL auto-generation is set to `validate` to ensure that entities strictly match the Flyway-managed schema.

---

## Running Tests
Run the JUnit unit tests using:
```powershell
.\mvnw.cmd test
```

To run both unit and integration tests, verify formatting, and enforce code coverage checks:
```powershell
.\mvnw.cmd verify
```

* **Code Coverage Gate:** The project uses JaCoCo to enforce a **minimum instruction coverage of 80%** on all core logic. Exclusions are defined consistently for both local builds and SonarCloud for non-business boilerplate code (bootstrap, config beans, custom exceptions, entities, records, and the Playwright driver).

---

## GitHub CI Pipeline
The project includes a unified GitHub Actions workflow to verify code quality, security, and test correctness:
- **Workflow Configuration:** [.github/workflows/ci.yml](.github/workflows/ci.yml)
- **Included Checks:**
  - **Secrets Leak Prevention:** TruffleHog scanner checks commit histories.
  - **Dependency & Code Security:** Snyk Open Source & Code scans.
  - **Format Check:** Spotless verification.
  - **Automated Testing:** Unit and Integration tests (JaCoCo 80% coverage check).
  - **Static Code Analysis:** Sends metrics to SonarCloud.

### Required Secrets
To run security scans and SonarCloud analysis in CI, configure the following secrets in your GitHub repository:
- `SNYK_TOKEN`: Snyk API token.
- `SONAR_TOKEN`: SonarCloud authentication token.

---

## AI Agent Context
* Refer to [AGENTS.md](AGENTS.md) for code styling guidelines, SOLID rules, and compiler requirements.

