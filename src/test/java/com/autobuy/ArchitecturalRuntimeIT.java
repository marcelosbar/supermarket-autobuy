package com.autobuy;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.repository.PriceHistoryRepository;
import com.autobuy.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"autobuy.secrets-path=target/test-secrets.properties"})
@ActiveProfiles("test")
class ArchitecturalRuntimeIT {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	@Autowired
	private EntityManager entityManager;

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
}
