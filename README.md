# Supermarket Auto-Buy

Supermarket Auto-Buy is a modular, AI-first Java console application powered by **Spring Boot** and **Microsoft Playwright**. It automates online grocery shopping (starting with **Continente Online**) by reading a structured shopping list, logging into the store, searching for items, mapping search queries to exact product SKUs, logging price history in a local H2 database, and adding the items to the shopping cart.

---

## Features

1. **Robust Playwright Automation:** Automates logging in, accepting cookies, performing product queries, and managing cart additions on Continente Online.
2. **Interactive Match Mapping:** If a shopping list item doesn't have an exact SKU mapped in the database, the CLI queries the store, presents the top matches, and lets you choose the correct product. Your choice is saved for subsequent runs.
3. **Price Tracking & H2 Storage:** Keeps historical records of all item prices from each run in a local, file-persisted H2 database (`data/db.mv.db`).
4. **OneDrive-Safe Snapshot Backups:** Automatically dumps a compressed ZIP/SQL backup of the database to a configured folder (e.g. your OneDrive folder) on application shutdown to avoid live file-locking issues.
5. **AI-First & SOLID Compliance:** Modular architecture using clean interfaces (`SupermarketDriver`, `CredentialProvider`, `ShoppingListProvider`) and type-safety. Exposes instructions for AI development in `AGENTS.md` and a backlog roadmap in `ROADMAP.md`.

---

## Getting Started

### Prerequisites
* **Java:** JDK 25 or higher.
* **Internet Connection:** Playwright will automatically download browser binaries (Chromium) on the first run.

### 1. Setup Credentials
Create a file named `secrets.properties` in the root directory of the project (this file is excluded from Git). Add your Continente Online credentials:

```properties
continente.username=your-email@example.com
continente.password=your-password
```

*Note: If these properties are empty or the file is missing, the application will prompt you securely in the terminal at runtime.*

### 2. Create your Shopping List
Copy the template `shopping-list-example.json` to `shopping-list.json` in the root directory (this file is excluded from Git to keep your personal shopping list private):

```json
[
  {
    "query": "Mimosa Leite Meio Gordo 1L",
    "quantity": 6
  },
  {
    "query": "Banana Importada kg",
    "quantity": 1
  }
]
```

### 3. Run the Application

#### Web UI Mode (Default)
By default, launching the application will start the local Web server:
```powershell
# Start the Web UI server
.\mvnw.cmd spring-boot:run
```
Then, open your browser and navigate to:
**`http://localhost:8080`**

From this premium single-page dashboard, you can:
* Manage items in your shopping list directly with instant saving.
* Configure and save credentials securely without manual properties file edits.
* View and delete product mappings from the local database.
* Execute active shopping runs, monitor progress via logs, select product matches interactively via modal cards, and close the session when done.



---

## Database Snapshot Backup
By default, the database is persisted locally in `./data/db.mv.db`. On shutdown, a zipped backup is written to `./data/backups/backup_[timestamp].zip`.

To sync your backups automatically to **OneDrive**, configure the backup directory in your `application.properties` (or add it directly in `secrets.properties` to keep paths private):

```properties
autobuy.backup-dir=C:/Users/your-username/OneDrive/SupermarketBackup
```

---

## Running Tests
Run the JUnit unit and integration tests using:

```powershell
.\mvnw.cmd test
```

*   **Code Coverage Gate:** The project uses JaCoCo to enforce a **minimum instruction coverage of 80%** on all core logic (excluding drivers and CLI entry points). If your edits cause the coverage to fall below 80%, the Maven test phase will fail.

---

## AI Agent & Roadmap Contexts
*   Refer to [AGENTS.md](file:///c:/git/supermarket-autobuy/AGENTS.md) for code styling guidelines, SOLID rules, and compiler requirements.
*   Refer to [ROADMAP.md](file:///c:/git/supermarket-autobuy/ROADMAP.md) for the backlog of future integrations (e.g. Google Keep/Tasks, Bitwarden, email invoice parsing, etc.).
