# Detailed Architectural Analysis — Milestone 1 Refactoring

## Executive Summary
This report analyzes the core codebase of the Supermarket Auto-Buy application to propose concrete design and implementation plans for Milestone 1. It details the resolution of a SOLID/DIP violation in credential saving, the extraction of product/mapping and price logging into transactional service layers, and optimization of entity fetching and dependency injection.

---

## 1. CredentialProvider DIP Refactoring

### Current State & SOLID Violation
In `WebApiController.java` (lines 101–114), credential saving is handled via an explicit type check and downcast to `PropertiesCredentialProvider`:
```java
if (credentialProvider instanceof PropertiesCredentialProvider propertiesProvider) {
    propertiesProvider.saveCredentials(request.supermarket(), request.username(), request.password());
    ...
} else {
    log.error("SOLID Exception: CredentialProvider is not an instance of PropertiesCredentialProvider. Cannot save dynamically.");
    ...
}
```
This violates the **Dependency Inversion Principle (DIP)**: the controller depends on the concrete implementation `PropertiesCredentialProvider` rather than the abstraction `CredentialProvider`. If a different credential provider (e.g., database-backed) is introduced, the controller code must be modified.

### Proposed Changes

#### A. Exceptions Definition
Define a custom runtime exception hierarchy under a new package `com.autobuy.exception`:

**`AutoBuyException.java`**:
```java
package com.autobuy.exception;

/**
 * Base exception for the Supermarket Auto-Buy application.
 */
public class AutoBuyException extends RuntimeException {
    public AutoBuyException(String message) {
        super(message);
    }

    public AutoBuyException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**`CredentialException.java`**:
```java
package com.autobuy.exception;

/**
 * Exception thrown when credential operations fail.
 */
public class CredentialException extends AutoBuyException {
    public CredentialException(String message) {
        super(message);
    }

    public CredentialException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

#### B. `CredentialProvider` Interface Update
Modify `src/main/java/com/autobuy/provider/CredentialProvider.java` to declare `saveCredentials` as a default method:
```java
package com.autobuy.provider;

import com.autobuy.exception.CredentialException;

public interface CredentialProvider {
    String getUsername(String supermarket);
    String getPassword(String supermarket);

    /**
     * Saves credentials for a supermarket.
     *
     * @param supermarket The name of the supermarket (e.g., "CONTINENTE")
     * @param username    The username (email)
     * @param password    The password
     * @throws CredentialException           if saving fails.
     * @throws UnsupportedOperationException if the operation is not supported by the provider.
     */
    default void saveCredentials(String supermarket, String username, String password) throws CredentialException {
        throw new UnsupportedOperationException("saveCredentials not supported by this provider");
    }
}
```

#### C. `PropertiesCredentialProvider` Update
Modify `src/main/java/com/autobuy/provider/PropertiesCredentialProvider.java` to override `saveCredentials` and implement the interface:
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
            throw new CredentialException("Failed to save credentials to properties file", e);
        }
    }
```

#### D. `WebApiController` Update
Refactor `WebApiController.java` to remove the `instanceof` check and delegate directly to `CredentialProvider`:
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
            log.error("Credentials saving not supported by current provider", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Database/properties credentials saving not supported in this profile.");
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

## 2. ProductService Extraction

### Current State & Duplication
The business logic to map a search query to a supermarket product and save it is currently duplicated inside `AutoBuyWebService.saveMapping` and `AutoBuyCommandLineRunner.saveMapping`. Both methods directly interact with `ProductRepository` and `ProductMappingRepository`.

### Analysis of Gaps in the Planned Signatures
The `ProductService` interface defined in `PROJECT.md` / `SCOPE.md` specifies the following methods:
1. `@Transactional ProductMapping saveMapping(ProductMapping mapping)`
2. `Optional<ProductMapping> findMapping(String supermarket, String externalId)`
3. `@Transactional void deleteMapping(Long id)`
4. `@Transactional Product findOrCreateProduct(String name, String ean, String brand)`

Upon review of the domain constraints:
* **The `findOrCreateProduct` parameter gap:** The `Product` entity uses a composite logical uniqueness constraint on `(externalId, supermarket)`. Creating a new product in the database without specifying a `supermarket` (since `supermarket` is a non-null database column) will cause a database constraint violation. Furthermore, the parameter `ean` represents the barcode/SKU, which maps to `externalId` in the entity.
* **The `findMapping` parameter gap:** Callers currently search for mappings using `item.query()` (the search query text), not the external SKU (`externalProductId`). The signature `findMapping(String supermarket, String externalId)` where `externalId` is searched instead of query text would fail to find the query mappings.

### Proposed Solution and Design
We recommend overloading or extending the signatures to ensure logical consistency and prevent compilation/database failures.

#### Interface: `src/main/java/com/autobuy/service/ProductService.java`
```java
package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import java.util.Optional;

public interface ProductService {
    ProductMapping saveMapping(ProductMapping mapping);
    
    // Finds mapping by search text/query
    Optional<ProductMapping> findMappingBySearchText(String supermarket, String searchText);
    
    // As defined in project contract (overloaded for SKU-based mapping lookup)
    Optional<ProductMapping> findMapping(String supermarket, String externalId);
    
    void deleteMapping(Long id);
    
    // As defined in project contract (with fallback or default supermarket logic)
    Product findOrCreateProduct(String name, String ean, String brand);
    
    // RECOMMENDED: Full domain signature supporting supermarket context, URL, and category
    Product findOrCreateProduct(String externalId, String supermarket, String name, String brand, String url, String category);
}
```

#### Implementation: `src/main/java/com/autobuy/service/ProductServiceImpl.java`
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
    @Transactional(readOnly = true)
    public Optional<ProductMapping> findMappingBySearchText(String supermarket, String searchText) {
        return productMappingRepository.findBySearchTextAndSupermarket(searchText, supermarket);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductMapping> findMapping(String supermarket, String externalId) {
        // Fallback or SKU-based mapping search
        return productMappingRepository.findAll().stream()
                .filter(m -> m.getSupermarket().equalsIgnoreCase(supermarket) && m.getExternalProductId().equals(externalId))
                .findFirst();
    }

    @Override
    @Transactional
    public void deleteMapping(Long id) {
        if (productMappingRepository.existsById(id)) {
            productMappingRepository.deleteById(id);
        }
    }

    @Override
    @Transactional
    public Product findOrCreateProduct(String name, String ean, String brand) {
        // Fallback logic for contract compliance (defaulting supermarket context to "CONTINENTE")
        return findOrCreateProduct(ean, "CONTINENTE", name, brand, null, null);
    }

    @Override
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

## 3. PriceHistoryService Extraction

### Current State
Price history logging logic is duplicated in `AutoBuyWebService.logPrice` and `AutoBuyCommandLineRunner.logPrice`. These methods directly look up or create products and then save new `PriceHistory` records.

### Proposed Solution and Design

#### Interface: `src/main/java/com/autobuy/service/PriceHistoryService.java`
```java
package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PriceHistoryService {
    PriceHistory logPrice(Product product, BigDecimal price, LocalDateTime timestamp);
}
```

#### Implementation: `src/main/java/com/autobuy/service/PriceHistoryServiceImpl.java`
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

## 4. Model and Provider Refactoring (R5)

### A. LAZY Fetch Type on `PriceHistory.product`
Modify `src/main/java/com/autobuy/model/PriceHistory.java` at line 18:

**Before**:
```java
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
```

**After**:
```java
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
```
This defers database loads of `Product` details when querying price history records, optimizing memory consumption and SQL operations.

### B. Injecting `ObjectMapper` in `JsonShoppingListProvider`
Modify `src/main/java/com/autobuy/provider/JsonShoppingListProvider.java` to inject `ObjectMapper` via constructor instead of direct instantiation (`new ObjectMapper()`).

**Before**:
```java
    private final ObjectMapper objectMapper = new ObjectMapper();
```

**After**:
```java
    private final ObjectMapper objectMapper;

    public JsonShoppingListProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
```
This complies with Spring's dependency injection rules, allowing the application to share a single pre-configured Jackson bean, which also respects custom serializations/deserializations.

---

## 5. Integration and Unit Testing Strategy

All new services must be tested to satisfy the **80% instruction coverage gate via JaCoCo**.

### New Unit Tests

#### `ProductServiceImplTest.java`
```java
package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceImplTest {

    private ProductRepository productRepository;
    private ProductMappingRepository productMappingRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productMappingRepository = mock(ProductMappingRepository.class);
        productService = new ProductServiceImpl(productRepository, productMappingRepository);
    }

    @Test
    void testSaveMapping() {
        ProductMapping mapping = new ProductMapping("query", "CONTINENTE", "SKU123", "Product 1");
        when(productMappingRepository.save(mapping)).thenReturn(mapping);

        ProductMapping saved = productService.saveMapping(mapping);
        assertNotNull(saved);
        verify(productMappingRepository, times(1)).save(mapping);
    }

    @Test
    void testFindMappingBySearchText() {
        ProductMapping mapping = new ProductMapping("query", "CONTINENTE", "SKU123", "Product 1");
        when(productMappingRepository.findBySearchTextAndSupermarket("query", "CONTINENTE"))
                .thenReturn(Optional.of(mapping));

        Optional<ProductMapping> found = productService.findMappingBySearchText("CONTINENTE", "query");
        assertTrue(found.isPresent());
        assertEquals("SKU123", found.get().getExternalProductId());
    }

    @Test
    void testDeleteMapping() {
        Long mappingId = 1L;
        when(productMappingRepository.existsById(mappingId)).thenReturn(true);
        doNothing().when(productMappingRepository).deleteById(mappingId);

        productService.deleteMapping(mappingId);
        verify(productMappingRepository, times(1)).deleteById(mappingId);
    }

    @Test
    void testFindOrCreateProduct_Existing() {
        Product existing = new Product("SKU123", "CONTINENTE", "Product 1", "Brand 1", "http://test", "Cat 1");
        when(productRepository.findByExternalIdAndSupermarket("SKU123", "CONTINENTE"))
                .thenReturn(Optional.of(existing));

        Product product = productService.findOrCreateProduct("Product 1", "SKU123", "Brand 1");
        assertEquals("SKU123", product.getExternalId());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void testFindOrCreateProduct_New() {
        Product newProduct = new Product("SKU123", "CONTINENTE", "Product 1", "Brand 1", "http://test", "Cat 1");
        when(productRepository.findByExternalIdAndSupermarket("SKU123", "CONTINENTE"))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(newProduct);

        Product product = productService.findOrCreateProduct("SKU123", "CONTINENTE", "Product 1", "Brand 1", "http://test", "Cat 1");
        assertNotNull(product);
        verify(productRepository, times(1)).save(any(Product.class));
    }
}
```

#### `PriceHistoryServiceImplTest.java`
```java
package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.repository.PriceHistoryRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceHistoryServiceImplTest {

    @Test
    void testLogPrice() {
        PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
        PriceHistoryService service = new PriceHistoryServiceImpl(repository);
        Product product = new Product("SKU123", "CONTINENTE", "Product 1", "Brand 1", "http://test", "Cat 1");
        BigDecimal price = new BigDecimal("10.99");
        LocalDateTime now = LocalDateTime.now();

        PriceHistory history = new PriceHistory(product, price, now, "SCRAPE");
        when(repository.save(any(PriceHistory.class))).thenReturn(history);

        PriceHistory saved = service.logPrice(product, price, now);
        assertNotNull(saved);
        assertEquals(price, saved.getPrice());
        verify(repository, times(1)).save(any(PriceHistory.class));
    }
}
```
