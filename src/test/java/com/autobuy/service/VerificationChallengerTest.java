package com.autobuy.service;

import com.autobuy.exception.CredentialException;
import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.provider.PropertiesCredentialProvider;
import com.autobuy.repository.PriceHistoryRepository;
import com.autobuy.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"autobuy.secrets-path=target/temp-secrets.properties",
		"spring.jpa.properties.hibernate.generate_statistics=true"})
class VerificationChallengerTest {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	@Autowired
	private ProductService productService;

	@Autowired
	private PropertiesCredentialProvider credentialProvider;

	@Autowired
	private TxTestHelper txTestHelper;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void setUp() {
		// Clean up files and repositories
		File file = new File("target/temp-secrets.properties");
		if (file.exists()) {
			file.delete();
		}
		// Reset the internal properties of the credential provider
		credentialProvider.init();

		priceHistoryRepository.deleteAll();
		productRepository.deleteAll();
	}

	// ==========================================
	// 1. TRANSACTIONAL BOUNDARIES VERIFICATION
	// ==========================================

	@Test
	void testTransactionRollbackOnRuntimeException() {
		String externalId = "TX-ERR-123";
		assertFalse(productRepository.findByExternalIdAndSupermarket(externalId, "CONTINENTE").isPresent());

		// Trigger exception inside transaction and assert it propagates
		assertThrows(RuntimeException.class, () -> {
			txTestHelper.runTxWithException(externalId, "Rollback Product");
		});

		// Verify that transaction indeed rolled back and no product was persisted
		assertFalse(productRepository.findByExternalIdAndSupermarket(externalId, "CONTINENTE").isPresent());
	}

	@Test
	void testTransactionCommitOnSuccess() {
		String externalId = "TX-OK-123";
		assertFalse(productRepository.findByExternalIdAndSupermarket(externalId, "CONTINENTE").isPresent());

		// Run success scenario inside transaction
		txTestHelper.runTxWithSuccess(externalId, "Commit Product");

		// Verify product is committed and saved in database
		assertTrue(productRepository.findByExternalIdAndSupermarket(externalId, "CONTINENTE").isPresent());
	}

	// ==========================================
	// 2. SAVECREDENTIALS ROBUSTNESS VERIFICATION
	// ==========================================

	@Test
	void testSaveCredentials_ValidationChecks() {
		// Supermarket validation
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials(null, "user", "pass");
		});
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("", "user", "pass");
		});
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("   ", "user", "pass");
		});

		// Username validation
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("CONTINENTE", null, "pass");
		});
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("CONTINENTE", "", "pass");
		});
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("CONTINENTE", "   ", "pass");
		});

		// Password validation
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("CONTINENTE", "user", null);
		});
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("CONTINENTE", "user", "");
		});
		assertThrows(CredentialException.class, () -> {
			credentialProvider.saveCredentials("CONTINENTE", "user", "   ");
		});
	}

	@Test
	void testSaveCredentials_SuccessAndCaseInsensitivity() throws Exception {
		// Save valid credentials with mixed case supermarket
		credentialProvider.saveCredentials("cOnTiNeNtE", "user@test.com", "my-secure-password");

		// Verify that it normalizes to lowercase and stores correctly
		assertEquals("user@test.com", credentialProvider.getUsername("CONTINENTE"));
		assertEquals("user@test.com", credentialProvider.getUsername("continente"));
		assertEquals("my-secure-password", credentialProvider.getPassword("continente"));

		// Reinitialize provider from file to verify persistence
		PropertiesCredentialProvider newProvider = new PropertiesCredentialProvider();
		org.springframework.test.util.ReflectionTestUtils.setField(newProvider, "secretsPath",
				"target/temp-secrets.properties");
		newProvider.init();

		assertEquals("user@test.com", newProvider.getUsername("continente"));
		assertEquals("my-secure-password", newProvider.getPassword("continente"));
	}

	// ==========================================
	// 3. PRICEHISTORY LAZY LOADING VERIFICATION
	// ==========================================

	@Test
	@Transactional
	public void testPriceHistoryLazyLoadingBehavior() {
		// Arrange: create a product and log its price
		Product product = new Product("SKU-LAZY-TEST", "CONTINENTE", "Lazy Test Product", "Brand", "http://url",
				"Category");
		productRepository.save(product);

		PriceHistory history = new PriceHistory(product, new BigDecimal("1.99"), LocalDateTime.now(), "SCRAPE");
		priceHistoryRepository.save(history);

		entityManager.flush();
		entityManager.clear(); // Clear Hibernate first-level cache to force database fetch

		// Act: Load the PriceHistory
		PriceHistory loadedHistory = priceHistoryRepository.findById(history.getId()).orElseThrow();

		// Assert: Verify proxy behavior
		Product proxyProduct = loadedHistory.getProduct();
		assertNotNull(proxyProduct);

		// Assert that the class is indeed a HibernateProxy subclass (e.g.
		// Product$HibernateProxy$...)
		assertTrue(proxyProduct.getClass().getName().contains("HibernateProxy"),
				"Expected product to be a HibernateProxy, but was: " + proxyProduct.getClass().getName());

		// Check if it is initialized initially
		boolean isInitializedInitially = org.hibernate.Hibernate.isInitialized(proxyProduct);
		System.out.println("--- CHALLENGE 2 DIAGNOSTIC ---");
		System.out.println("Initially initialized: " + isInitializedInitially);

		// Access the ID via getId() getter
		Long id = proxyProduct.getId();
		boolean isInitializedAfterGetId = org.hibernate.Hibernate.isInitialized(proxyProduct);
		System.out.println("Initialized after getId(): " + isInitializedAfterGetId);
		assertFalse(isInitializedAfterGetId, "Calling getId() on the proxy should NOT trigger initialization");

		// Call toString() and check if it triggers initialization
		String toStringResult = loadedHistory.toString();
		boolean isInitializedAfterToString = org.hibernate.Hibernate.isInitialized(proxyProduct);
		System.out.println("Initialized after toString(): " + isInitializedAfterToString);
		assertFalse(isInitializedAfterToString,
				"Calling toString() on PriceHistory should NOT initialize the Product proxy because it only accesses the ID.");

		// Access another field (name) via getName() getter
		String name = proxyProduct.getName();
		boolean isInitializedAfterGetName = org.hibernate.Hibernate.isInitialized(proxyProduct);
		System.out.println("Initialized after getName(): " + isInitializedAfterGetName);
		System.out.println("------------------------------");

		// Assertions based on lazy configuration and field access
		assertFalse(isInitializedInitially, "Hibernate proxy should NOT be initialized initially");
		assertTrue(isInitializedAfterGetName, "Accessing name field should trigger initialization");
	}

	// Test helper configuration and class for Transactional boundary checks
	@TestConfiguration
	static class HelperConfig {
		@Bean
		public TxTestHelper txTestHelper() {
			return new TxTestHelper();
		}
	}

	public static class TxTestHelper {
		@Autowired
		private ProductService productService;

		@Transactional
		public void runTxWithException(String externalId, String name) {
			productService.findOrCreateProduct(externalId, "CONTINENTE", name, "Brand", "http://url", "Category");
			throw new RuntimeException("Forced exception for transaction rollback verification");
		}

		@Transactional
		public void runTxWithSuccess(String externalId, String name) {
			productService.findOrCreateProduct(externalId, "CONTINENTE", name, "Brand", "http://url", "Category");
		}
	}
}
