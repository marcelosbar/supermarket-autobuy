package com.autobuy.service;

import com.autobuy.exception.CredentialException;
import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.provider.PropertiesCredentialProvider;
import com.autobuy.repository.PriceHistoryRepository;
import com.autobuy.repository.ProductRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"autobuy.secrets-path=target/test-secrets.properties"})
@ActiveProfiles("test")
@Import(ArchitectureStandardsVerificationIT.TestHelperService.class)
class ArchitectureStandardsVerificationIT {

	@Autowired
	private TestHelperService testHelperService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	@Autowired
	private PropertiesCredentialProvider propertiesCredentialProvider;

	@PersistenceContext
	private EntityManager entityManager;

	@Component
	public static class TestHelperService {
		@Autowired
		private ProductService productService;

		@Transactional
		public void createProductAndThrow(String externalId, String supermarket, String name) {
			productService.findOrCreateProduct(externalId, supermarket, name, "TestBrand", null, null);
			throw new RuntimeException("Simulated exception to trigger rollback");
		}
	}

	@Test
	void testTransactionRollbackOnRuntimeException() {
		String externalId = "TX-ROLLBACK-TEST";
		String supermarket = "CONTINENTE";

		// Verify it doesn't exist before
		assertFalse(productRepository.findByExternalIdAndSupermarket(externalId, supermarket).isPresent());

		// Call the service method that creates and then throws
		assertThrows(RuntimeException.class, () -> {
			testHelperService.createProductAndThrow(externalId, supermarket, "Should Rollback Product");
		});

		// Verify that the product creation was rolled back
		Optional<Product> rolledBackProduct = productRepository.findByExternalIdAndSupermarket(externalId, supermarket);
		assertFalse(rolledBackProduct.isPresent(), "Product should not be saved due to transactional rollback");
	}

	@Test
	void testSaveCredentialsValidationAndRobustness() {
		// Test edge cases for saveCredentials validation
		assertThrows(CredentialException.class, () -> {
			propertiesCredentialProvider.saveCredentials(null, "user", "pass");
		});
		assertThrows(CredentialException.class, () -> {
			propertiesCredentialProvider.saveCredentials("  ", "user", "pass");
		});
		assertThrows(CredentialException.class, () -> {
			propertiesCredentialProvider.saveCredentials("CONTINENTE", null, "pass");
		});
		assertThrows(CredentialException.class, () -> {
			propertiesCredentialProvider.saveCredentials("CONTINENTE", "", "pass");
		});
		assertThrows(CredentialException.class, () -> {
			propertiesCredentialProvider.saveCredentials("CONTINENTE", "user", null);
		});
		assertThrows(CredentialException.class, () -> {
			propertiesCredentialProvider.saveCredentials("CONTINENTE", "user", "  ");
		});

		// Test normal saving and retrieving
		assertDoesNotThrow(() -> {
			propertiesCredentialProvider.saveCredentials("CONTINENTE", "validUser", "validPass");
			assertEquals("validUser", propertiesCredentialProvider.getUsername("CONTINENTE"));
			assertEquals("validPass", propertiesCredentialProvider.getPassword("CONTINENTE"));
		});

		// Test IO exception handling when path is a directory (causing write error)
		PropertiesCredentialProvider errorProvider = new PropertiesCredentialProvider();
		assertDoesNotThrow(() -> {
			java.lang.reflect.Field field = PropertiesCredentialProvider.class.getDeclaredField("secretsPath");
			field.setAccessible(true);
			field.set(errorProvider, "target/"); // 'target/' is a directory, writing to it as a file will throw
													// IOException

			assertThrows(CredentialException.class, () -> {
				errorProvider.saveCredentials("CONTINENTE", "user", "pass");
			});
		});
	}

	@Test
	@Transactional
	void testPriceHistoryLazyLoadingAndNPlusOneQueryAvoidance() {
		// Create product and price history
		Product product = new Product("SKU-LAZY", "CONTINENTE", "Lazy Product", "Brand", "http://url", "Cat");
		Product savedProduct = productRepository.save(product);

		PriceHistory history = new PriceHistory(savedProduct, new BigDecimal("10.99"), LocalDateTime.now(), "SCRAPE");
		PriceHistory savedHistory = priceHistoryRepository.save(history);

		// Clear persistence context to force loading from database
		entityManager.flush();
		entityManager.clear();

		// Retrieve price history
		PriceHistory retrievedHistory = priceHistoryRepository.findById(savedHistory.getId())
				.orElseThrow(() -> new AssertionError("Saved history not found"));

		// Check lazy-loaded proxy
		Product lazyProduct = retrievedHistory.getProduct();
		assertNotNull(lazyProduct);
		assertFalse(Hibernate.isInitialized(lazyProduct), "Product proxy should NOT be initialized initially");

		// Verify that calling toString() does NOT initialize the proxy
		String representation = retrievedHistory.toString();
		assertNotNull(representation);
		assertFalse(Hibernate.isInitialized(lazyProduct),
				"Product proxy should still NOT be initialized after toString()");
		assertTrue(representation.contains("product=" + savedProduct.getId()), "toString must output only product ID");

		// Verify that accessing product ID directly on the proxy does not initialize
		// the proxy
		Long lazyId = lazyProduct.getId();
		assertEquals(savedProduct.getId(), lazyId);
		assertFalse(Hibernate.isInitialized(lazyProduct),
				"Product proxy should NOT be initialized when accessing its ID");

		// Verify that accessing other fields (e.g., name) initializes the proxy
		String lazyName = lazyProduct.getName();
		assertEquals("Lazy Product", lazyName);
		assertTrue(Hibernate.isInitialized(lazyProduct),
				"Product proxy should be initialized when accessing non-ID properties");
	}
}
