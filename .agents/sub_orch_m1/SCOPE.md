# Scope: Milestone 1 (Model & Service Refactoring)

## Architecture
- Refactoring Wave 1 focuses on dependency inversion, extracting core service layers, cleaning DTO packages, implementing custom exception hierarchies, and initializing Flyway database migration baseline.
- For Milestone 1, we focus on:
  - DIP Fix for `CredentialProvider` and `PropertiesCredentialProvider`.
  - Extracting `ProductService`.
  - Extracting `PriceHistoryService`.
  - Lazy fetching in `PriceHistory` and injecting `ObjectMapper` in `JsonShoppingListProvider`.
  - Writing new unit tests.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1.1 | Credential Saving DIP Fix | R1: `saveCredentials` in `CredentialProvider` interface and implement in `PropertiesCredentialProvider`. | None | PLANNED |
| 1.2 | ProductService Extraction | R2: Create `ProductService` with transactional mapping operations. | None | PLANNED |
| 1.3 | PriceHistoryService Extraction | R3: Create `PriceHistoryService` with transactional `logPrice`. | None | PLANNED |
| 1.4 | Model & Provider Refactoring | R5: LAZY fetch type on `PriceHistory.product` and inject `ObjectMapper`. | None | PLANNED |
| 1.5 | Unit Testing | Verify behavior with new unit tests for services. | None | PLANNED |

## Interface Contracts
### CredentialProvider
- Added method signature: `default void saveCredentials(String supermarket, String username, String password) throws CredentialException`
- By default, throws `UnsupportedOperationException("saveCredentials not supported by this provider")`.

### ProductService
- Methods:
  - `@Transactional ProductMapping saveMapping(ProductMapping mapping)`
  - `Optional<ProductMapping> findMapping(String supermarket, String externalId)`
  - `@Transactional void deleteMapping(Long id)`
  - `@Transactional Product findOrCreateProduct(String name, String ean, String brand)`

### PriceHistoryService
- Methods:
  - `@Transactional PriceHistory logPrice(Product product, java.math.BigDecimal price, java.time.LocalDateTime timestamp)`
