# Project: Supermarket Auto-Buy Refactoring (Wave 1)

## Architecture
- Refactoring Wave 1 focuses on dependency inversion, extracting core service layers, cleaning DTO packages, implementing custom exception hierarchies, and initializing Flyway database migration baseline.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Model & Service Refactoring | Implement R1 (Credential saving DIP Fix), R2 (Extract ProductService), R3 (Extract PriceHistoryService), and R5 (PriceHistory LAZY fetching & ObjectMapper injection). | None | DONE |
| 2 | Web, Exception & Log Refactoring | Implement R4 (Extract DTOs to web/dto/), R6 (Replace MemoryAppender with logback-spring.xml), and R7 (Custom exception hierarchy & GlobalExceptionHandler). | Milestone 1 | PLANNED |
| 3 | Database Migration (Flyway) | Implement R8 (Add Flyway database migrations and baseline schema). | Milestone 1, 2 | PLANNED |

## Interface Contracts
### CredentialProvider
- Added: `default void saveCredentials(String supermarket, String username, String password) throws CredentialException` (throws `UnsupportedOperationException` by default).

### ProductService
- Methods:
  - `@Transactional ProductMapping saveMapping(ProductMapping mapping)`
  - `Optional<ProductMapping> findMapping(String supermarket, String externalId)`
  - `@Transactional void deleteMapping(Long id)`
  - `@Transactional Product findOrCreateProduct(String name, String ean, String brand)`

### PriceHistoryService
- Methods:
  - `@Transactional PriceHistory logPrice(Product product, java.math.BigDecimal price, java.time.LocalDateTime timestamp)`

### Exception Hierarchy
- Base: `AutoBuyException` (extends `RuntimeException`)
- Subclasses: `DriverException`, `CredentialException`, `ShoppingListException`
