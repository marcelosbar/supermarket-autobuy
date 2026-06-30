# Handoff Report: Milestone 1 (Model & Service Refactoring)

## 1. Observation
- **Dependency Inversion Principle (DIP) Violation**:
  In `src/main/java/com/autobuy/web/WebApiController.java` lines 101-114:
  ```java
  if (credentialProvider instanceof PropertiesCredentialProvider propertiesProvider) {
      propertiesProvider.saveCredentials(request.supermarket(), request.username(), request.password());
      ...
  }
  ```
  The controller directly casts `CredentialProvider` to its implementation `PropertiesCredentialProvider` to save credentials.
- **Duplicated Business Logic**:
  In `src/main/java/com/autobuy/web/AutoBuyWebService.java` (lines 365-397) and `src/main/java/com/autobuy/cli/AutoBuyCommandLineRunner.java` (lines 235-271), the helper methods `saveMapping` and `logPrice` contain identical transactional database operations querying and updating repositories directly.
- **Contract/Schema Mismatches**:
  - `Product` entity has `@Column(nullable = false) private String supermarket` and a unique constraint on `(externalId, supermarket)`. However, the requested `ProductService.findOrCreateProduct(String name, String ean, String brand)` lacks a `supermarket` parameter.
  - `PriceHistory` entity in `src/main/java/com/autobuy/model/PriceHistory.java` at line 18 uses eager fetch:
    ```java
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    ```
- **Manual Object Instantiation**:
  In `src/main/java/com/autobuy/provider/JsonShoppingListProvider.java` line 23:
  ```java
  private final ObjectMapper objectMapper = new ObjectMapper();
  ```
  And in `src/main/java/com/autobuy/web/WebApiController.java` line 34:
  ```java
  private final ObjectMapper objectMapper = new ObjectMapper();
  ```

---

## 2. Logic Chain
1. Checking `instanceof` and casting to `PropertiesCredentialProvider` in `WebApiController` makes it depend on a concrete implementation, violating DIP. Defining `saveCredentials(String supermarket, String username, String password) throws CredentialException` in `CredentialProvider` with a default exception-throwing body restores interface-based decoupling.
2. Directly calling repositories and repeating the `saveMapping` and `logPrice` operations across CLI and web layers violates SRP and code reusability. Creating `ProductService` and `PriceHistoryService` will consolidate this business logic.
3. Trying to save a `Product` entity without a `supermarket` fails DB integrity checks (since it is a non-nullable field). Thus, the signature of `findOrCreateProduct` must be changed to `findOrCreateProduct(String name, String ean, String brand, String supermarket)`.
4. EAGER fetching of `product` in `PriceHistory` leads to unnecessary database queries when loading price log entries. Changing this to `FetchType.LAZY` mitigates N+1 queries.
5. Manually instantiating `ObjectMapper` bypasses Spring Boot's unified JSON configuration. Injecting it via constructor injection utilizes the application context's customized bean.

---

## 3. Caveats
- No code was modified in the source files, as the task is purely investigative.
- We assumed that the parameter `ean` in `findOrCreateProduct` represents the `externalId` (SKU) of the `Product` entity.

---

## 4. Conclusion
The codebase needs to be refactored as proposed in `analysis.md`:
1. Add `saveCredentials` to `CredentialProvider` interface and implement it in `PropertiesCredentialProvider` (throwing `CredentialException` on failure).
2. Clean up `WebApiController.saveCredentials` by invoking the interface directly.
3. Extract `ProductService` (with `findOrCreateProduct` adjusted to take `supermarket`) and `PriceHistoryService`.
4. Refactor `PriceHistory` to `FetchType.LAZY` and inject `ObjectMapper` in `JsonShoppingListProvider` and `WebApiController`.

---

## 5. Verification Method
1. Run `.\mvnw.cmd clean package` to check that the project packages successfully.
2. Run `.\mvnw.cmd test` to verify all unit/integration tests pass and the JaCoCo coverage remains >= 80%.
3. Run `.\mvnw.cmd spotless:apply` to verify the Eclipse JDT formatting rules are correctly preserved.
