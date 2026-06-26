# Supermarket Auto-Buy: Future Feature Roadmap

This document outlines the backlog of features planned for future iterations. These will be moved to GitHub Issues once the repository is pushed.

---

## 1. Google Tasks / Keep Integration (Shopping List)
* **Goal:** Allow voice-driven shopping lists via Google Assistant/Nest without manual copy-pasting or file editing.
* **Details:**
  * Define `GoogleTasksShoppingListProvider` implementing `ShoppingListProvider`.
  * Integrate with the official Google Tasks Java API client library.
  * Fetch tasks from a specific shopping list (e.g. "Supermarket List") and map them to our domain model.
  * Design OAuth2 credential flow to securely store Google user tokens.

## 2. Bitwarden CLI Integration (Secrets)
* **Goal:** Eliminate plaintext storage of Continente Online passwords on disk.
* **Details:**
  * Define `BitwardenCredentialProvider` implementing `CredentialProvider`.
  * Execute background CLI commands using Java's `ProcessBuilder` (e.g., `bw get password "Continente Online"`).
  * Handle session keys (`BW_SESSION` environment variable) and prompt the user to unlock the vault in the CLI if locked.

## 3. Continente Email Order Confirmation Parser
* **Goal:** Seed the database with years of historical pricing data using old order email confirmations.
* **Details:**
  * Add a parser service (`ContinenteEmailInvoiceParser`) that reads `.eml` or `.html` email exports.
  * Extract item names, quantities, unit prices, and order dates.
  * Match products to the database and write to `PriceHistory` and `Order` tables.
  * Optional: Connect to an IMAP folder to automatically pull new order confirmation emails.

## 4. Price History Analytics & Web Dashboard
* **Goal:** Provide visual analytics and warning systems if item prices increase.
* **Details:**
  * Add a Spring Boot Web / Thymeleaf interface or a local REST API.
  * Generate charts of specific products showing price trends.
  * Implement an alert system in the checkout CLI runner: if a product is more than 10% more expensive than its average historical price, print a warning in the terminal asking for approval.
