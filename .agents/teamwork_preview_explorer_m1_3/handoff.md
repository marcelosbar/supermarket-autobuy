# Handoff Report — Explorer 3 (Milestone 1)

## 1. Observation
We observed the following exact file locations, constraints, and dependencies:
* **Credential Saving Downcast**: In `src/main/java/com/autobuy/web/WebApiController.java` (lines 101-102):
  ```java
  if (credentialProvider instanceof PropertiesCredentialProvider propertiesProvider) {
      propertiesProvider.saveCredentials(request.supermarket(), request.username(), request.password());
  ```
* **CredentialProvider Interface**: Located at `src/main/java/com/autobuy/provider/CredentialProvider.java`. It defines:
  ```java
  public interface CredentialProvider {
      String getUsername(String supermarket);
      String getPassword(String supermarket);
  }
  ```
* **Duplicated Product & Mapping Saving Logic**: Located in `src/main/java/com/autobuy/web/AutoBuyWebService.java` (lines 365-380) and `src/main/java/com/autobuy/cli/AutoBuyCommandLineRunner.java` (lines 235-253) as a private method `saveMapping`.
* **Duplicated Price Logging Logic**: Located in `src/main/java/com/autobuy/web/AutoBuyWebService.java` (lines 382-397) and `src/main/java/com/autobuy/cli/AutoBuyCommandLineRunner.java` (lines 255-271) as a private method `logPrice`.
* **PriceHistory Fetch Type**: In `src/main/java/com/autobuy/model/PriceHistory.java` (line 18):
  ```java
  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  ```
* **JsonShoppingListProvider Mapper Instantiation**: In `src/main/java/com/autobuy/provider/JsonShoppingListProvider.java` (line 23):
  ```java
  private final ObjectMapper objectMapper = new ObjectMapper();
  ```
* **pom.xml Rules**: Spotless Maven Plugin enforces JDT Eclipse formatting, and JaCoCo enforces a strict 80% instruction coverage gate on all non-excluded packages:
  ```xml
  <minimum>0.80</minimum>
  ```
  ```xml
  <excludes>
      <exclude>com/autobuy/driver/**</exclude>
      <exclude>com/autobuy/cli/**</exclude>
      <exclude>com/autobuy/web/**</exclude>
      <exclude>com/autobuy/SupermarketAutobuyApplication.class</exclude>
  </excludes>
  ```
* **Baseline Tests Execution**: Running `.\mvnw.cmd test` passes 24 unit tests cleanly with 100% build success.

---

## 2. Logic Chain
1. **DIP Violations**: The `WebApiController` directly performs an `instanceof` check against `PropertiesCredentialProvider`. According to the Dependency Inversion Principle, client classes should depend on interfaces, not implementations. Therefore, declaring a default method `saveCredentials` in `CredentialProvider` (throwing `UnsupportedOperationException` by default) allows the controller to interact solely with the interface.
2. **ProductService Extraction**: The duplication of mapping and product finding/creation in `AutoBuyWebService` and `AutoBuyCommandLineRunner` indicates that these operations belong to a shared transactional business layer. Extracting them to `ProductService` improves cohesion.
3. **ProductService Signature Discrepancies**:
   * The `findOrCreateProduct(String name, String ean, String brand)` signature lacks the non-null `supermarket` context needed by the composite uniqueness constraint and database schema. Therefore, the implementation must overload or default the supermarket argument to prevent database constraint failures.
   * `findMapping(String supermarket, String externalId)` uses `externalId` but callers need to lookup mappings using `item.query()` (searchText). Therefore, a `findMappingBySearchText` method must be exposed.
4. **PriceHistoryService Extraction**: The duplicate `logPrice` method in callers can be cleanly extracted to `PriceHistoryService.logPrice(...)` to encapsulate history tracking.
5. **PriceHistory Optimization**: Changing `PriceHistory.product` fetch type to `LAZY` avoids eager loading of related product properties.
6. **DI Optimization**: Injecting `ObjectMapper` in `JsonShoppingListProvider` allows configuring a single shared instance rather than hardcoding resource-heavy instance creation.
7. **Coverage Requirement**: Since the new classes belong to `com.autobuy.service.*` (which is not excluded by JaCoCo configuration), they must be covered by unit tests to meet the 80% instruction coverage target.

---

## 3. Caveats
* **CredentialException**: Because the custom exception classes do not exist in the current codebase, they must be created under `com.autobuy.exception` in Milestone 1 to compile.
* **Supermarket Context**: We assume that if `findOrCreateProduct` is called using the strict signature (without a supermarket name), a fallback supermarket like `"CONTINENTE"` is used to avoid null constraint database violations. We strongly recommend using the recommended overloaded signature.

---

## 4. Conclusion
Milestone 1 refactoring is fully defined. We recommend:
1. Creating `AutoBuyException` and `CredentialException` in `com.autobuy.exception`.
2. Adding `saveCredentials` default method to `CredentialProvider` and implementing it in `PropertiesCredentialProvider`.
3. Updating `WebApiController` to remove `instanceof` downcasts.
4. Designing and implementing `ProductService` and `PriceHistoryService` along with their implementations in `com.autobuy.service`.
5. Modifying callers `AutoBuyWebService` and `AutoBuyCommandLineRunner` to use these services.
6. Changing `PriceHistory` fetch type to `LAZY`.
7. Constructor-injecting `ObjectMapper` into `JsonShoppingListProvider`.
8. Implementing unit tests for the new services to satisfy the 80% JaCoCo coverage check.

---

## 5. Verification Method
1. **Compilation & Formatting**: Run `.\mvnw.cmd spotless:apply` and `.\mvnw.cmd clean package` to ensure clean formatting and successful build.
2. **Unit Tests**: Run `.\mvnw.cmd test` to execute all unit tests and verify the 80% JaCoCo covered ratio check passes.
