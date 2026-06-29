# Empirical Verification and Stress Test Results

This document presents the findings, methodology, and verification results conducted by **Challenger 2** for Milestone 1.

All verifications were executed by running a custom, comprehensive test suite `com.autobuy.service.VerificationChallengerTest` against the active Spring Boot container and JPA/Hibernate context.

---

## 1. Verification of Transactional Boundaries

### Goal
Ensure that transactions are correctly managed by Spring (i.e., rolling back on RuntimeExceptions, committing on successful completion, and propagating transactions across service boundaries).

### Methodology
- Created `TxTestHelper` running inside the Spring Test context, annotated with `@Transactional`.
- **Rollback Test**: Inside a transaction, called `ProductService.findOrCreateProduct` to insert a new product with `externalId = "TX-ERR-123"`, then threw a `RuntimeException`. Verified that the transaction rolled back and the database did not persist the new product.
- **Commit Test**: Inside a transaction, called `ProductService.findOrCreateProduct` with `externalId = "TX-OK-123"` and completed successfully. Verified that the transaction committed and the product was successfully saved.

### Results
- `testTransactionRollbackOnRuntimeException`: **PASSED** (Product `TX-ERR-123` was not found in repository).
- `testTransactionCommitOnSuccess`: **PASSED** (Product `TX-OK-123` was successfully found in repository).
- Spring `@Transactional` proxies and transaction managers are functioning correctly.

### Verdict
**PASS**

---

## 2. Verification of `saveCredentials` Robustness

### Goal
Test `PropertiesCredentialProvider.saveCredentials` with edge cases, invalid inputs, mixed case normalization, and file persistence.

### Methodology
- **Input Validation Checks**: Direct method calls to `saveCredentials` with combinations of:
  - `supermarket` as `null`, empty `""`, or whitespace `"   "`.
  - `username` as `null`, empty `""`, or whitespace `"   "`.
  - `password` as `null`, empty `""`, or whitespace `"   "`.
  - Verified they all throw `CredentialException`.
- **Case-Insensitivity & Normalization**: Saved credentials using a mixed-case supermarket name `"cOnTiNeNtE"` and checked that it was retrievable using `"CONTINENTE"` and `"continente"`.
- **File Persistence & Loading**: Verified that saving dynamically writes to the file system (configured to a safe path `target/temp-secrets.properties`) and that a new instance of `PropertiesCredentialProvider` successfully reloads those properties from the file on initialization.
- **Client/API Level Checks**: Inspected `WebApiController.saveCredentials` which routes requests to `CredentialProvider.saveCredentials`.

### Results
- Null, empty, and whitespace values for all fields throw a `CredentialException` properly.
- Normalization to lowercase is active for supermarket names.
- File write/store works correctly.
- **Architectural Observation / Robustness Gap**:
  - The API endpoint `/api/credentials` catches `CredentialException` and returns a `500 Internal Server Error`. While the input validation inside `PropertiesCredentialProvider` is robust, invalid client requests (e.g., sending empty usernames) trigger a `500` HTTP status rather than a `400 Bad Request`. This should be handled at the controller level or via a global exception handler.

### Verdict
**PASS** (Robust validation in place, with a recommended API response improvement).

---

## 3. Verification of `PriceHistory` Lazy Loading Behavior

### Goal
Verify that the `PriceHistory -> Product` `@ManyToOne` association is lazy-loaded, behaves correctly, does not initialize unless requested, and does not cause unexpected N+1 query execution (specifically during `toString()` operations).

### Methodology
- Inserted a product and its associated price history record.
- Cleared the Hibernate L1 cache (`entityManager.clear()`) to guarantee any subsequent entity reads require hitting the database (or the proxy).
- Fetched the `PriceHistory` record.
- Inspected the class name of the associated `Product` to verify it is a HibernateProxy (e.g. `Product$HibernateProxy`).
- Verified initialization state (`Hibernate.isInitialized(proxy)`) at various stages:
  1. Immediately after retrieval (should be `false`).
  2. After calling `proxyProduct.getId()` (should be `false` if optimized, since the ID is already known to the proxy).
  3. After calling `loadedHistory.toString()`, which prints the product ID (should be `false`).
  4. After calling `proxyProduct.getName()` (should be `true` as name requires database lookup).

### Results
Diagnostic output from test run:
```
--- CHALLENGE 2 DIAGNOSTIC ---
Initially initialized: false
Initialized after getId(): false
Initialized after toString(): false
Initialized after getName(): true
------------------------------
```
- **Analysis**:
  - The proxy is initially uninitialized, confirming that `FetchType.LAZY` works.
  - Calling `proxyProduct.getId()` does **not** trigger initialization. This is because Hibernate recognizes `getId()` as the identifier getter of the proxy and returns the cached key without a database query.
  - Calling `loadedHistory.toString()` does **not** trigger initialization. This means printing `PriceHistory` does not suffer from N+1 query triggers.
  - Accessing non-id fields (e.g., `getName()`) correctly triggers proxy initialization.

### Verdict
**PASS**
