package com.autobuy;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.repository.PriceHistoryRepository;
import com.autobuy.repository.ProductRepository;
import com.autobuy.service.ProductService;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"autobuy.secrets-path=target/test-secrets.properties"})
@ActiveProfiles("test")
@Import(ArchitecturalRuntimeIT.TxTestHelperConfig.class)
class ArchitecturalRuntimeIT {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	@Autowired
	private TxTestHelper txTestHelper;

	@Autowired
	private EntityManager entityManager;

	@Test
	void testTransactionRollbackOnRuntimeException() {
		String externalId = "TX-ROLLBACK-RUNTIME";
		String supermarket = "CONTINENTE";

		assertFalse(productRepository.findByExternalIdAndSupermarket(externalId, supermarket).isPresent());

		assertThrows(RuntimeException.class,
				() -> txTestHelper.createProductAndThrow(externalId, supermarket, "Should Rollback"));

		Optional<Product> rolledBack = productRepository.findByExternalIdAndSupermarket(externalId, supermarket);
		assertFalse(rolledBack.isPresent(), "Product creation should be rolled back when RuntimeException is thrown");
	}

	@Test
	void testTransactionCommitOnSuccess() {
		String externalId = "TX-COMMIT-RUNTIME";
		String supermarket = "CONTINENTE";

		assertFalse(productRepository.findByExternalIdAndSupermarket(externalId, supermarket).isPresent());

		txTestHelper.createProductSuccessfully(externalId, supermarket, "Should Commit");

		Optional<Product> committed = productRepository.findByExternalIdAndSupermarket(externalId, supermarket);
		assertTrue(committed.isPresent(), "Product creation should be committed on successful execution");
	}

	@Test
	@Transactional
	void testPriceHistoryLazyLoadingAndProxyBehavior() {
		Product product = new Product("SKU-LAZY-RT", "CONTINENTE", "Lazy Runtime Product", "Brand", "http://url",
				"Cat");
		Product savedProduct = productRepository.save(product);

		PriceHistory history = new PriceHistory(savedProduct, new BigDecimal("15.99"), LocalDateTime.now(), "SCRAPE");
		PriceHistory savedHistory = priceHistoryRepository.save(history);

		entityManager.flush();
		entityManager.clear();

		PriceHistory retrievedHistory = priceHistoryRepository.findById(savedHistory.getId())
				.orElseThrow(() -> new AssertionError("Saved PriceHistory not found"));

		Product lazyProduct = retrievedHistory.getProduct();
		assertNotNull(lazyProduct);
		assertFalse(Hibernate.isInitialized(lazyProduct), "Product proxy should NOT be initialized initially");

		String representation = retrievedHistory.toString();
		assertNotNull(representation);
		assertFalse(Hibernate.isInitialized(lazyProduct), "Product proxy should NOT be initialized after toString()");
		assertTrue(representation.contains("product=" + savedProduct.getId()), "toString must output only product ID");

		Long lazyId = lazyProduct.getId();
		assertEquals(savedProduct.getId(), lazyId);
		assertFalse(Hibernate.isInitialized(lazyProduct), "Product proxy should NOT be initialized when accessing ID");

		String lazyName = lazyProduct.getName();
		assertEquals("Lazy Runtime Product", lazyName);
		assertTrue(Hibernate.isInitialized(lazyProduct),
				"Product proxy should be initialized when accessing non-ID properties");
	}

	@TestConfiguration
	static class TxTestHelperConfig {
		@Bean
		public TxTestHelper txTestHelper() {
			return new TxTestHelper();
		}
	}

	public static class TxTestHelper {
		@Autowired
		private ProductService productService;

		@Transactional
		public void createProductAndThrow(String externalId, String supermarket, String name) {
			productService.findOrCreateProduct(externalId, supermarket, name, "Brand", null, null);
			throw new RuntimeException("Simulated exception for rollback");
		}

		@Transactional
		public void createProductSuccessfully(String externalId, String supermarket, String name) {
			productService.findOrCreateProduct(externalId, supermarket, name, "Brand", null, null);
		}
	}
}
