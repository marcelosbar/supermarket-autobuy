# Architectural Analysis: Milestone 1 (Model & Service Refactoring)

## Executive Summary
This analysis addresses five key areas of architectural improvements to align the project with SOLID principles, dependency injection standards, and Java 25 / Spring Boot conventions.
1. **DIP Fix for Credential Saving**: Resolving a Dependency Inversion violation in `WebApiController` by promoting `saveCredentials` to the `CredentialProvider` interface.
2. **Product & Mapping Logic Extraction**: Consolidating duplicated mapping logic from `AutoBuyCommandLineRunner` and `AutoBuyWebService` into a new `ProductService`.
3. **Price Logging Logic Extraction**: Consolidating duplicate price-history logic into a new `PriceHistoryService`.
4. **Performance & DI Optimization**:
   - Optimizing database fetch behavior in `PriceHistory` (EAGER to LAZY).
   - Resolving manual instantiation of `ObjectMapper` in `JsonShoppingListProvider` and `WebApiController`.
5. **Unit Testing Strategy**: Outlining test coverage requirements (Mockito-based) to enforce the JaCoCo 80% coverage check.

---

## 1. Credential Saving DIP Fix

### Current Implementation & Violation
Currently, `CredentialProvider` is defined as:
```java
public interface CredentialProvider {
	String getUsername(String supermarket);
	String getPassword(String supermarket);
}
```
In `WebApiController.java` (lines 100-115), the `/api/credentials` POST endpoint attempts to save credentials. Since the interface lacks a saving contract, the controller performs a hard-cast check using `instanceof`:
```java
if (credentialProvider instanceof PropertiesCredentialProvider propertiesProvider) {
    propertiesProvider.saveCredentials(request.supermarket(), request.username(), request.password());
    ...
} else {
    log.error("SOLID Exception: CredentialProvider is not an instance of PropertiesCredentialProvider. Cannot save dynamically.");
    ...
}
```
This violates the **Dependency Inversion Principle (DIP)**: the high-level controller directly depends on a low-level implementation detail (`PropertiesCredentialProvider`) and uses reflection/type-casting to access implementation-specific behavior.

### Proposed Interface Changes
We will define `CredentialException` in `com.autobuy.exception` and add the `saveCredentials` contract to the `CredentialProvider` interface with a `default` implementation that throws `UnsupportedOperationException`:
```java
package com.autobuy.provider;

import com.autobuy.exception.CredentialException;

public interface CredentialProvider {
	String getUsername(String supermarket);
	String getPassword(String supermarket);

	/**
	 * Saves credentials for a supermarket.
	 *
	 * @throws CredentialException if saving fails.
	 * @throws UnsupportedOperationException if this provider does not support writing credentials.
	 */
	default void saveCredentials(String supermarket, String username, String password) throws CredentialException {
		throw new UnsupportedOperationException("saveCredentials not supported by this provider");
	}
}
```

### Proposed Class Implementation
In `PropertiesCredentialProvider.java`, we override this method and throw a `CredentialException` in case of file operations error:
```java
	@Override
	public synchronized void saveCredentials(String supermarket, String username, String password) throws CredentialException {
		properties.setProperty(supermarket.toLowerCase() + ".username", username);
		properties.setProperty(supermarket.toLowerCase() + ".password", password);
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(secretsPath)) {
			properties.store(fos, "Saved via Web UI");
			log.info("Successfully saved credentials for {} to {}", supermarket, secretsPath);
		} catch (IOException e) {
			log.error("Failed to save credentials for {} to {}", supermarket, secretsPath, e);
			throw new CredentialException("Failed to save credentials for " + supermarket, e);
		}
	}
```

In `WebApiController.java`, the casting code is replaced with a clean interface invocation:
```java
	@PostMapping("/credentials")
	public ResponseEntity<Map<String, Object>> saveCredentials(@RequestBody CredentialsRequest request) {
		try {
			credentialProvider.saveCredentials(request.supermarket(), request.username(), request.password());
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("message", "Credentials saved successfully.");
			return ResponseEntity.ok(response);
		} catch (UnsupportedOperationException e) {
			log.error("Credentials saving not supported by the current provider", e);
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "Credentials saving not supported in this profile.");
			return ResponseEntity.internalServerError().body(response);
		} catch (CredentialException e) {
			log.error("Failed to save credentials", e);
			Map<String, Object> response = new HashMap<>();
			response.put("success", false);
			response.put("message", "Failed to save credentials: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}
```

---

## 2. Product and Mapping Business Logic

### Current Code Duplication
Both `AutoBuyCommandLineRunner.java` and `AutoBuyWebService.java` contain duplicate methods:
- `saveMapping(String query, String supermarket, SearchResult result)`
- `logPrice(SearchResult result, String supermarket)`

They also directly interact with `ProductRepository`, `ProductMappingRepository`, and `PriceHistoryRepository`. This bypasses the service layer, violating the **Single Responsibility Principle (SRP)**.

### Analysis of the Interface Contract
The requested `ProductService` interface in `PROJECT.md` is:
```java
public interface ProductService {
    @Transactional ProductMapping saveMapping(ProductMapping mapping);
    Optional<ProductMapping> findMapping(String supermarket, String externalId);
    @Transactional void deleteMapping(Long id);
    @Transactional Product findOrCreateProduct(String name, String ean, String brand);
}
```

#### Identified Discrepancies & Resolutions:
1. **`findOrCreateProduct(String name, String ean, String brand)`**:
   - **Issue**: A `Product` entity has a database column `supermarket` marked as `nullable = false`. Saving a Product without a supermarket string causes database constraint violations. Additionally, `externalId` is unique per-supermarket.
   - **Resolution**: Introduce the supermarket string. The signature should be updated to:
     ```java
     @Transactional Product findOrCreateProduct(String name, String ean, String brand, String supermarket);
     ```
     *(Note: "ean" parameter maps directly to the `externalId` attribute of the `Product` entity).*
2. **`findMapping(String supermarket, String externalId)`**:
   - **Issue**: The CLI runner and web service search mappings using `searchText` and `supermarket`. The current repository lacks `findBySupermarketAndExternalProductId`.
   - **Resolution**:
     - Implement `findMapping` by querying mapping by SKU/externalProductId: `productMappingRepository.findBySupermarketAndExternalProductId`.
     - Also introduce `Optional<ProductMapping> findMappingBySearchText(String supermarket, String searchText)` to support the core lookup logic used by the autobuy workflows.

### Proposed Code for `ProductService.java`
```java
package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface ProductService {
	@Transactional
	ProductMapping saveMapping(ProductMapping mapping);

	Optional<ProductMapping> findMapping(String supermarket, String externalProductId);

	Optional<ProductMapping> findMappingBySearchText(String supermarket, String searchText);

	@Transactional
	void deleteMapping(Long id);

	@Transactional
	Product findOrCreateProduct(String name, String ean, String brand, String supermarket);
}
```

### Proposed Code for `ProductServiceImpl.java`
```java
package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProductServiceImpl implements ProductService {

	private final ProductRepository productRepository;
	private final ProductMappingRepository productMappingRepository;

	public ProductServiceImpl(ProductRepository productRepository, ProductMappingRepository productMappingRepository) {
		this.productRepository = productRepository;
		this.productMappingRepository = productMappingRepository;
	}

	@Override
	@Transactional
	public ProductMapping saveMapping(ProductMapping mapping) {
		return productMappingRepository.save(mapping);
	}

	@Override
	public Optional<ProductMapping> findMapping(String supermarket, String externalProductId) {
		return productMappingRepository.findBySupermarketAndExternalProductId(supermarket, externalProductId);
	}

	@Override
	public Optional<ProductMapping> findMappingBySearchText(String supermarket, String searchText) {
		return productMappingRepository.findBySearchTextAndSupermarket(searchText.toLowerCase().trim(), supermarket);
	}

	@Override
	@Transactional
	public void deleteMapping(Long id) {
		productMappingRepository.deleteById(id);
	}

	@Override
	@Transactional
	public Product findOrCreateProduct(String name, String ean, String brand, String supermarket) {
		return productRepository.findByExternalIdAndSupermarket(ean, supermarket)
				.orElseGet(() -> {
					Product newProduct = new Product(ean, supermarket, name, brand, null, null);
					return productRepository.save(newProduct);
				});
	}
}
```
*Note: We must add `Optional<ProductMapping> findBySupermarketAndExternalProductId(String supermarket, String externalProductId)` to `ProductMappingRepository.java` to support `findMapping`.*

---

## 3. Price Logging Business Logic

### Current Code Duplication
Similar to mapping, logging prices involves looking up/saving a `Product` and then saving a `PriceHistory` entry.

### Proposed Code for `PriceHistoryService.java`
```java
package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PriceHistoryService {
	@Transactional
	PriceHistory logPrice(Product product, BigDecimal price, LocalDateTime timestamp);
}
```

### Proposed Code for `PriceHistoryServiceImpl.java`
```java
package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PriceHistoryServiceImpl implements PriceHistoryService {

	private final PriceHistoryRepository priceHistoryRepository;

	public PriceHistoryServiceImpl(PriceHistoryRepository priceHistoryRepository) {
		this.priceHistoryRepository = priceHistoryRepository;
	}

	@Override
	@Transactional
	public PriceHistory logPrice(Product product, BigDecimal price, LocalDateTime timestamp) {
		PriceHistory history = new PriceHistory(product, price, timestamp, "SCRAPE");
		return priceHistoryRepository.save(history);
	}
}
```

---

## 4. PriceHistory and ObjectMapper DI Fixes

### A. Lazy Fetching in PriceHistory
In `src/main/java/com/autobuy/model/PriceHistory.java`:
```java
	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;
```
This is a performance issue because retrieving price history always triggers an eager join query to load product data. We must change it to `FetchType.LAZY`:
```java
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
```

### B. ObjectMapper Injection
In `src/main/java/com/autobuy/provider/JsonShoppingListProvider.java` and `src/main/java/com/autobuy/web/WebApiController.java`, the `ObjectMapper` is manually instantiated:
```java
private final ObjectMapper objectMapper = new ObjectMapper();
```
To conform with Spring Boot auto-configuration, we should inject the framework's configured `ObjectMapper` bean via constructors.

#### Refactored `JsonShoppingListProvider.java`:
```java
package com.autobuy.provider;

import com.autobuy.model.ShoppingItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JsonShoppingListProvider implements ShoppingListProvider {

	private static final Logger log = LoggerFactory.getLogger(JsonShoppingListProvider.class);

	private final ObjectMapper objectMapper;

	public JsonShoppingListProvider(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public List<ShoppingItem> getShoppingList(String sourcePath) {
		File file = new File(sourcePath);
		if (!file.exists()) {
			log.error("Shopping list file not found: {}", sourcePath);
			return Collections.emptyList();
		}

		try {
			List<ShoppingItem> items = objectMapper.readValue(file, new TypeReference<List<ShoppingItem>>() {});
			log.info("Loaded {} items from shopping list: {}", items.size(), sourcePath);
			return items;
		} catch (IOException e) {
			log.error("Failed to parse shopping list file: {}", sourcePath, e);
			return Collections.emptyList();
		}
	}
}
```

---

## 5. Unit Testing Strategy (Milestone 1.5)

### A. PropertiesCredentialProviderTest.java
Add tests to verify:
1. `saveCredentials` writes properties file successfully.
2. `saveCredentials` handles I/O exceptions appropriately and throws `CredentialException`.

### B. JsonShoppingListProviderTest.java
Update construction of `JsonShoppingListProvider` to pass `new ObjectMapper()` into the constructor:
```java
private final JsonShoppingListProvider provider = new JsonShoppingListProvider(new ObjectMapper());
```

### C. ProductServiceTest.java (New)
Test suite verifying:
- `findOrCreateProduct` behavior (saving when absent, returning when present).
- `saveMapping` and `deleteMapping` transactions.

### D. PriceHistoryServiceTest.java (New)
Test suite verifying:
- `logPrice` saves a `PriceHistory` entry with the correct parameters (product, price, timestamp, "SCRAPE").
