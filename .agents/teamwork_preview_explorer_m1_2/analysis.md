# Detailed Analysis - Milestone 1 Refactoring

This document provides a detailed investigation and architectural analysis of the refactoring tasks for Milestone 1.

## 1. Components Located

### A. CredentialProvider & PropertiesCredentialProvider
* **CredentialProvider (Interface):** Located at `src/main/java/com/autobuy/provider/CredentialProvider.java`
  * Defines `getUsername(String supermarket)` and `getPassword(String supermarket)`.
  * Lacks a method to save credentials, leading to downstream DIP violations.
* **PropertiesCredentialProvider (Implementation):** Located at `src/main/java/com/autobuy/provider/PropertiesCredentialProvider.java`
  * Implements `CredentialProvider`.
  * Implements a `public synchronized void saveCredentials(String supermarket, String username, String password)` method (lines 58-67), but it is not part of the interface contract.

### B. WebApiController
* Located at `src/main/java/com/autobuy/web/WebApiController.java`
* Exposes REST endpoints for credentials, shopping lists, mappings, and run actions.
* **DIP Violation (lines 101-114):**
  ```java
  if (credentialProvider instanceof PropertiesCredentialProvider propertiesProvider) {
      propertiesProvider.saveCredentials(request.supermarket(), request.username(), request.password());
      ...
  } else {
      log.error("SOLID Exception: CredentialProvider is not an instance of PropertiesCredentialProvider...");
      ...
  }
  ```
  The controller checks if `credentialProvider` is an instance of `PropertiesCredentialProvider` and casts it to call `saveCredentials`. This violates the Dependency Inversion Principle because the controller directly depends on a concrete provider implementation.

### C. PriceHistory Entity
* Located at `src/main/java/com/autobuy/model/PriceHistory.java`
* **Eager Fetch Violation (line 18):**
  ```java
  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;
  ```
  This loads the associated `Product` eagerly, which can cause performance issues or N+1 queries when loading multiple `PriceHistory` records.

### D. JsonShoppingListProvider
* Located at `src/main/java/com/autobuy/provider/JsonShoppingListProvider.java`
* **Hardcoded Mapper Instantiation (line 23):**
  ```java
  private final ObjectMapper objectMapper = new ObjectMapper();
  ```
  This violates Spring's dependency injection principles by manually instantiating `ObjectMapper` instead of utilizing the container's configured mapper.

---

## 2. Product and Mapping Business Logic Refactoring

### Current State
Duplicate product and mapping logic resides in `AutoBuyWebService.java` (lines 245-280, 365-380) and `AutoBuyCommandLineRunner.java` (lines 130-172, 235-253).
Specifically, `saveMapping(...)` handles checking/saving the product entity in `ProductRepository` before saving the query mapping in `ProductMappingRepository`.

### ProductService Design
To centralize and encapsulate this logic in a SOLID manner, we will create `ProductService` class in the `service/` package:

```java
package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service encapsulating all product and mapping business logic.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMappingRepository productMappingRepository;

    public ProductService(ProductRepository productRepository, ProductMappingRepository productMappingRepository) {
        this.productRepository = productRepository;
        this.productMappingRepository = productMappingRepository;
    }

    @Transactional
    public ProductMapping saveMapping(ProductMapping mapping) {
        return productMappingRepository.save(mapping);
    }

    @Transactional(readOnly = true)
    public Optional<ProductMapping> findMapping(String supermarket, String externalId) {
        return productMappingRepository.findBySupermarketAndExternalProductId(supermarket, externalId);
    }

    @Transactional
    public void deleteMapping(Long id) {
        productMappingRepository.deleteById(id);
    }

    @Transactional
    public Product findOrCreateProduct(String name, String ean, String brand) {
        // Fallback default: since EAN maps to externalId and the entity requires supermarket,
        // we default to "CONTINENTE" for products created via this specific signature contract.
        return productRepository.findByExternalIdAndSupermarket(ean, "CONTINENTE")
                .orElseGet(() -> {
                    Product newProduct = new Product(ean, "CONTINENTE", name, brand, null, null);
                    return productRepository.save(newProduct);
                });
    }

    // Helper method to support full logic encapsulation
    @Transactional(readOnly = true)
    public Optional<ProductMapping> findBySearchTextAndSupermarket(String searchText, String supermarket) {
        return productMappingRepository.findBySearchTextAndSupermarket(searchText, supermarket);
    }

    @Transactional(readOnly = true)
    public List<ProductMapping> findAllMappings() {
        return productMappingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public boolean existsMappingById(Long id) {
        return productMappingRepository.existsById(id);
    }

    @Transactional
    public Product findOrCreateProduct(String externalId, String supermarket, String name, String brand, String url, String category) {
        return productRepository.findByExternalIdAndSupermarket(externalId, supermarket)
                .orElseGet(() -> {
                    Product newProduct = new Product(externalId, supermarket, name, brand, url, category);
                    return productRepository.save(newProduct);
                });
    }
}
```

---

## 3. Price History Business Logic Refactoring

### Current State
Duplicated price logging logic resides in `AutoBuyWebService.java` (lines 285, 382-397) and `AutoBuyCommandLineRunner.java` (lines 177, 255-271).
It handles locating the product (creating it if not found) and saving a `PriceHistory` record.

### PriceHistoryService Design
We will extract this logic into a dedicated `PriceHistoryService` class in the `service/` package:

```java
package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service encapsulating price history logging business logic.
 */
@Service
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;

    public PriceHistoryService(PriceHistoryRepository priceHistoryRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public PriceHistory logPrice(Product product, BigDecimal price, LocalDateTime timestamp) {
        PriceHistory history = new PriceHistory(product, price, timestamp, "SCRAPE");
        return priceHistoryRepository.save(history);
    }
}
```

---

## 4. Refactoring Model & Provider

### A. PriceHistory fetch type
In `src/main/java/com/autobuy/model/PriceHistory.java`, change the fetch type of the `product` property to `LAZY`:
```java
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;
```

### B. Inject ObjectMapper in JsonShoppingListProvider
Inject `ObjectMapper` via constructor injection in `src/main/java/com/autobuy/provider/JsonShoppingListProvider.java`:
```java
	private final ObjectMapper objectMapper;

	public JsonShoppingListProvider(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}
```
Remove:
```java
	private final ObjectMapper objectMapper = new ObjectMapper();
```

---

## 5. Recommended Code Changes (Proposed Diff Patches)

### Patch 1: Custom exceptions
Create `src/main/java/com/autobuy/exception/AutoBuyException.java` and `CredentialException.java` to support interface signatures.

### Patch 2: Interface Contract Changes (`CredentialProvider.java`)
```diff
diff --git a/src/main/java/com/autobuy/provider/CredentialProvider.java b/src/main/java/com/autobuy/provider/CredentialProvider.java
index 1000000..2000000
--- a/src/main/java/com/autobuy/provider/CredentialProvider.java
+++ b/src/main/java/com/autobuy/provider/CredentialProvider.java
@@ -1,5 +1,7 @@
 package com.autobuy.provider;
 
+import com.autobuy.exception.CredentialException;
+
 /**
  * Interface for loading credentials for a supermarket.
  */
@@ -22,4 +24,18 @@ public interface CredentialProvider {
 	 * @return The password, or null if not found
 	 */
 	String getPassword(String supermarket);
+
+	/**
+	 * Saves credentials for the specified supermarket.
+	 *
+	 * @param supermarket The supermarket name
+	 * @param username The username/email
+	 * @param password The password
+	 * @throws CredentialException If saving credentials fails
+	 */
+	default void saveCredentials(String supermarket, String username, String password) throws CredentialException {
+		throw new UnsupportedOperationException("saveCredentials not supported by this provider");
+	}
 }
```

### Patch 3: `PropertiesCredentialProvider.java`
```diff
diff --git a/src/main/java/com/autobuy/provider/PropertiesCredentialProvider.java b/src/main/java/com/autobuy/provider/PropertiesCredentialProvider.java
index 1000000..2000000
--- a/src/main/java/com/autobuy/provider/PropertiesCredentialProvider.java
+++ b/src/main/java/com/autobuy/provider/PropertiesCredentialProvider.java
@@ -1,5 +1,6 @@
 package com.autobuy.provider;
 
+import com.autobuy.exception.CredentialException;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.springframework.beans.factory.annotation.Value;
@@ -55,8 +56,9 @@ public class PropertiesCredentialProvider implements CredentialProvider {
 	/**
 	 * Saves credentials for a supermarket back to the secrets.properties file.
 	 */
-	public synchronized void saveCredentials(String supermarket, String username, String password) {
+	@Override
+	public synchronized void saveCredentials(String supermarket, String username, String password) throws CredentialException {
 		properties.setProperty(supermarket.toLowerCase() + ".username", username);
 		properties.setProperty(supermarket.toLowerCase() + ".password", password);
 		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(secretsPath)) {
 			properties.store(fos, "Saved via Web UI");
@@ -64,5 +66,6 @@ public class PropertiesCredentialProvider implements CredentialProvider {
 		} catch (IOException e) {
 			log.error("Failed to save credentials for {} to {}", supermarket, secretsPath, e);
+			throw new CredentialException("Failed to save credentials to file", e);
 		}
 	}
 }
```

### Patch 4: `WebApiController.java`
```diff
diff --git a/src/main/java/com/autobuy/web/WebApiController.java b/src/main/java/com/autobuy/web/WebApiController.java
index 1000000..2000000
--- a/src/main/java/com/autobuy/web/WebApiController.java
+++ b/src/main/java/com/autobuy/web/WebApiController.java
@@ -3,6 +3,8 @@ package com.autobuy.web;
 import com.autobuy.model.ProductMapping;
 import com.autobuy.model.ShoppingItem;
 import com.autobuy.provider.CredentialProvider;
-import com.autobuy.provider.PropertiesCredentialProvider;
 import com.autobuy.provider.ShoppingListProvider;
-import com.autobuy.repository.ProductMappingRepository;
+import com.autobuy.service.ProductService;
+import com.autobuy.exception.CredentialException;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
@@ -31,3 +33,3 @@ public class WebApiController {
 	private final AutoBuyWebService autoBuyWebService;
-	private final ProductMappingRepository productMappingRepository;
+	private final ProductService productService;
 	private final CredentialProvider credentialProvider;
@@ -38,5 +40,5 @@ public class WebApiController {
-	public WebApiController(AutoBuyWebService autoBuyWebService, ProductMappingRepository productMappingRepository,
+	public WebApiController(AutoBuyWebService autoBuyWebService, ProductService productService,
 			CredentialProvider credentialProvider, ShoppingListProvider shoppingListProvider) {
 		this.autoBuyWebService = autoBuyWebService;
-		this.productMappingRepository = productMappingRepository;
+		this.productService = productService;
 		this.credentialProvider = credentialProvider;
@@ -70,3 +72,3 @@ public class WebApiController {
 	public ResponseEntity<List<ProductMapping>> getMappings() {
-		return ResponseEntity.ok(productMappingRepository.findAll());
+		return ResponseEntity.ok(productService.findAllMappings());
 	}
@@ -75,6 +77,6 @@ public class WebApiController {
 	public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
-		if (productMappingRepository.existsById(id)) {
-			productMappingRepository.deleteById(id);
+		if (productService.existsMappingById(id)) {
+			productService.deleteMapping(id);
 			log.info("Deleted product mapping ID {}", id);
 			return ResponseEntity.noContent().build();
 		}
@@ -100,16 +102,16 @@ public class WebApiController {
 	@PostMapping("/credentials")
 	public ResponseEntity<Map<String, Object>> saveCredentials(@RequestBody CredentialsRequest request) {
-		if (credentialProvider instanceof PropertiesCredentialProvider propertiesProvider) {
-			propertiesProvider.saveCredentials(request.supermarket(), request.username(), request.password());
-			Map<String, Object> response = new HashMap<>();
-			response.put("success", true);
-			response.put("message", "Credentials saved successfully.");
-			return ResponseEntity.ok(response);
-		} else {
-			log.error(
-					"SOLID Exception: CredentialProvider is not an instance of PropertiesCredentialProvider. Cannot save dynamically.");
-			Map<String, Object> response = new HashMap<>();
-			response.put("success", false);
-			response.put("message", "Database/properties credentials saving not supported in this profile.");
-			return ResponseEntity.internalServerError().body(response);
-		}
+		try {
+			credentialProvider.saveCredentials(request.supermarket(), request.username(), request.password());
+			Map<String, Object> response = new HashMap<>();
+			response.put("success", true);
+			response.put("message", "Credentials saved successfully.");
+			return ResponseEntity.ok(response);
+		} catch (UnsupportedOperationException e) {
+			Map<String, Object> response = new HashMap<>();
+			response.put("success", false);
+			response.put("message", "Database/properties credentials saving not supported in this profile.");
+			return ResponseEntity.internalServerError().body(response);
+		} catch (CredentialException e) {
+			Map<String, Object> response = new HashMap<>();
+			response.put("success", false);
+			response.put("message", "Failed to save credentials: " + e.getMessage());
+			return ResponseEntity.internalServerError().body(response);
+		}
 	}
```

### Patch 5: `ProductMappingRepository.java`
Add query method signature to `ProductMappingRepository.java`:
```java
Optional<ProductMapping> findBySupermarketAndExternalProductId(String supermarket, String externalProductId);
```
