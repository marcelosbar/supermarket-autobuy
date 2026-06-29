# Handoff Report - Milestone 1 Refactoring

## 1. Observation

Direct observations made in the codebase:
* **DIP Violation in WebApiController:** In `src/main/java/com/autobuy/web/WebApiController.java` (lines 101-114), the controller checks if the injected `CredentialProvider` is an instance of `PropertiesCredentialProvider` and casts it to call `saveCredentials(String, String, String)`:
  ```java
  if (credentialProvider instanceof PropertiesCredentialProvider propertiesProvider) {
      propertiesProvider.saveCredentials(request.supermarket(), request.username(), request.password());
      ...
  }
  ```
* **Missing saveCredentials Interface Contract:** The interface `src/main/java/com/autobuy/provider/CredentialProvider.java` does not contain the `saveCredentials` method.
* **Duplicated Product & Mapping Logic:** The logic to check for a product, create/save a product, and save a mapping is present in both:
  * `src/main/java/com/autobuy/web/AutoBuyWebService.java` (lines 365-380):
    ```java
    private void saveMapping(String query, String supermarket, SearchResult result) {
        try {
            Product product = productRepository.findByExternalIdAndSupermarket(result.externalId(), supermarket)
                    .orElseGet(() -> {
                        Product newProduct = new Product(result.externalId(), supermarket, result.name(),
                                result.brand(), result.url(), result.category());
                        return productRepository.save(newProduct);
                    });

            ProductMapping mapping = new ProductMapping(query, supermarket, result.externalId(), result.name());
            productMappingRepository.save(mapping);
            log.info("Saved product mapping: '{}' -> SKU: {}", query, result.externalId());
        } catch (Exception e) {
            log.error("Failed to save product mapping: {}", e.getMessage());
        }
    }
    ```
  * `src/main/java/com/autobuy/cli/AutoBuyCommandLineRunner.java` (lines 235-253) contains the identical `saveMapping` logic.
* **Duplicated Price Logging Logic:** The logic to log price history is present in both:
  * `src/main/java/com/autobuy/web/AutoBuyWebService.java` (lines 382-397):
    ```java
    private void logPrice(SearchResult result, String supermarket) {
        try {
            Product product = productRepository.findByExternalIdAndSupermarket(result.externalId(), supermarket)
                    .orElseGet(() -> {
                        Product newProduct = new Product(result.externalId(), supermarket, result.name(),
                                result.brand(), result.url(), result.category());
                        return productRepository.save(newProduct);
                    });

            PriceHistory history = new PriceHistory(product, result.price(), LocalDateTime.now(), "SCRAPE");
            priceHistoryRepository.save(history);
            log.info("Logged price for {}: {} €", product.getName(), result.price());
        } catch (Exception e) {
            log.error("Failed to log price history: {}", e.getMessage());
        }
    }
    ```
  * `src/main/java/com/autobuy/cli/AutoBuyCommandLineRunner.java` (lines 255-271) contains the identical `logPrice` logic.
* **Eager Fetching in PriceHistory:** In `src/main/java/com/autobuy/model/PriceHistory.java` (line 18):
  ```java
  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;
  ```
* **ObjectMapper Manual Instantiation:** In `src/main/java/com/autobuy/provider/JsonShoppingListProvider.java` (line 23):
  ```java
  private final ObjectMapper objectMapper = new ObjectMapper();
  ```

---

## 2. Logic Chain

1. **DIP Fix:** The `instanceof` check and class cast in `WebApiController` violates the Dependency Inversion Principle because the controller couples directly to the concrete `PropertiesCredentialProvider`. By defining `saveCredentials` in `CredentialProvider` interface (with a default throwing implementation) and implementing it in `PropertiesCredentialProvider`, we can eliminate the concrete check and cast.
2. **ProductService and PriceHistoryService Extraction:** The duplicate `saveMapping` and `logPrice` logic across `AutoBuyWebService` and `AutoBuyCommandLineRunner` violates the Single Responsibility Principle (SRP) and the Don't Repeat Yourself (DRY) principle. Creating a centralized transactional `ProductService` and `PriceHistoryService` encapsulates all mapping and logging business logic, which can then be injected and shared.
3. **Lazy Loading:** `PriceHistory` eagerly loads the associated `Product` entity. Eager loading is inefficient for high-volume logs like price history. Switching the fetch type to `FetchType.LAZY` resolves this issue.
4. **Jackson Mapper Injection:** Manually instantiating `ObjectMapper` within `JsonShoppingListProvider` prevents proper spring-managed configuration and testing mockability. Injecting it via constructor injection utilizes Spring's autowired beans and conforms to SOLID.

---

## 3. Caveats

* The custom exceptions `AutoBuyException` and `CredentialException` are not defined in the codebase yet. They must be created (recommended package: `com.autobuy.exception`) to prevent compilation errors when modifying `CredentialProvider` interface signature.
* A default supermarket name `"CONTINENTE"` is used in `ProductService.findOrCreateProduct(String name, String ean, String brand)` since the contract method signature lacks a supermarket parameter, while the `Product` entity model requires a non-null `supermarket` value.

---

## 4. Conclusion

The codebase currently contains architectural violations (DIP violations, duplication of transactional logic, eager database fetching, and manual dependency instantiation).
The exact changes needed are:
1. Define unchecked exceptions `AutoBuyException` and `CredentialException`.
2. Add `saveCredentials` to `CredentialProvider` interface and implement it in `PropertiesCredentialProvider`.
3. Simplify `WebApiController` to use the interface method `saveCredentials` directly.
4. Create `ProductService` to encapsulate all mapping/product transactional logic and repository operations.
5. Create `PriceHistoryService` to handle transactional price history logging.
6. Replace repository dependencies with `ProductService` and `PriceHistoryService` in `WebApiController`, `AutoBuyWebService`, and `AutoBuyCommandLineRunner`.
7. Change `PriceHistory.product` fetch type to `LAZY`.
8. Inject `ObjectMapper` via constructor injection in `JsonShoppingListProvider`.

---

## 5. Verification Method

To verify these changes after implementation:
1. **Clean build and package:**
   ```powershell
   .\mvnw.cmd clean package
   ```
2. **Execute tests (verifying JaCoCo >80% instruction coverage):**
   ```powershell
   .\mvnw.cmd test
   ```
3. **Verify style formatting:**
   ```powershell
   .\mvnw.cmd spotless:apply
   ```
4. **Inspect Files:**
   * Confirm `CredentialProvider` does not import `PropertiesCredentialProvider`.
   * Confirm `PriceHistory` has `fetch = FetchType.LAZY`.
   * Confirm `JsonShoppingListProvider` constructor accepts `ObjectMapper`.
