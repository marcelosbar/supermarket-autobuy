# Supermarket Auto-Buy: Future Feature Roadmap

This document outlines the backlog of features planned for future iterations. Each item is tracked as a GitHub Issue.

---

## 1. Google Tasks / Keep Integration (Shopping List)
**Issue:** [#22 feat: Google Tasks / Keep Integration](https://github.com/marcelosbar/supermarket-autobuy/issues/22)

* **Goal:** Allow voice-driven shopping lists via Google Assistant/Nest without manual copy-pasting or file editing.
* **Details:**
  * Define `GoogleTasksShoppingListProvider` implementing `ShoppingListProvider`.
  * Integrate with the official Google Tasks Java API client library.
  * Fetch tasks from a specific shopping list (e.g. "Supermarket List") and map them to our domain model.
  * Design OAuth2 credential flow to securely store Google user tokens.

## 2. Bitwarden CLI Integration (Secrets)
**Issue:** [#23 feat: Bitwarden CLI Integration](https://github.com/marcelosbar/supermarket-autobuy/issues/23)

* **Goal:** Eliminate plaintext storage of Continente Online passwords on disk.
* **Details:**
  * Define `BitwardenCredentialProvider` implementing `CredentialProvider`.
  * Execute background CLI commands using Java's `ProcessBuilder` (e.g., `bw get password "Continente Online"`).
  * Handle session keys (`BW_SESSION` environment variable) and prompt the user to unlock the vault in the Web UI if locked.

## 3. Continente Email Order Confirmation Parser
**Issue:** [#24 feat: Continente Email Order Confirmation Parser](https://github.com/marcelosbar/supermarket-autobuy/issues/24)

* **Goal:** Seed the database with years of historical pricing data using old order email confirmations.
* **Details:**
  * Add a parser service (`ContinenteEmailInvoiceParser`) that reads `.eml` or `.html` email exports.
  * Extract item names, quantities, unit prices, and order dates.
  * Match products to the database and write to `PriceHistory` and `Order` tables.
  * Optional: Connect to an IMAP folder to automatically pull new order confirmation emails.

## 4. Web Dashboard (Simple UI) ✅
* **Goal:** Provide a local web-based interface to manage the shopping list, view mappings, and execute/monitor the auto-buy scraper interactively.
* **Details:**
  * Add a Spring Boot Web server serving a Single Page Application (SPA).
  * Expose REST endpoints to read/update the shopping list (`shopping-list.json`), manage database product mappings, and monitor active runs.
  * Implement background runner pausing and UI prompting to select products when a mapping is missing.

## 5. Price History Analytics & Checkout Alerts
**Issue:** [#25 feat: Price History Analytics & Checkout Price Alerts](https://github.com/marcelosbar/supermarket-autobuy/issues/25)

* **Goal:** Provide visual analytics and warning systems if item prices increase.
* **Details:**
  * Generate charts of specific products showing price trends in the dashboard.
  * Implement an alert system in the auto-buy runner: if a product is more than 10% more expensive than its average historical price, trigger a warning in the Web UI asking for approval.
