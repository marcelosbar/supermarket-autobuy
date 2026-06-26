package com.autobuy.repository;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class SupermarketAutobuyRepositoryTest {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductMappingRepository productMappingRepository;

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	@Test
	void testSaveAndFindProduct() {
		// Arrange
		Product product = new Product("2001923", "CONTINENTE", "Mimosa Leite Meio Gordo 1L", "Mimosa",
				"https://continente.pt/mimosa", "Leitaria");

		// Act
		Product saved = productRepository.save(product);
		Optional<Product> found = productRepository.findByExternalIdAndSupermarket("2001923", "CONTINENTE");

		// Assert
		assertNotNull(saved.getId());
		assertTrue(found.isPresent());
		assertEquals("Mimosa Leite Meio Gordo 1L", found.get().getName());
	}

	@Test
	void testSaveAndFindMapping() {
		// Arrange
		ProductMapping mapping = new ProductMapping("mimosa meio gordo", "CONTINENTE", "2001923",
				"Mimosa Leite Meio Gordo 1L");

		// Act
		ProductMapping saved = productMappingRepository.save(mapping);
		Optional<ProductMapping> found = productMappingRepository.findBySearchTextAndSupermarket("mimosa meio gordo",
				"CONTINENTE");

		// Assert
		assertNotNull(saved.getId());
		assertTrue(found.isPresent());
		assertEquals("2001923", found.get().getExternalProductId());
	}

	@Test
	void testPriceHistoryLogging() {
		// Arrange
		Product product = new Product("2001923", "CONTINENTE", "Mimosa Leite Meio Gordo 1L", "Mimosa",
				"https://continente.pt/mimosa", "Leitaria");
		productRepository.save(product);

		PriceHistory log1 = new PriceHistory(product, new BigDecimal("1.49"), LocalDateTime.now().minusDays(1),
				"SCRAPE");

		PriceHistory log2 = new PriceHistory(product, new BigDecimal("1.55"), LocalDateTime.now(), "SCRAPE");

		// Act
		priceHistoryRepository.save(log1);
		priceHistoryRepository.save(log2);

		List<PriceHistory> history = priceHistoryRepository.findByProductOrderByRecordedAtDesc(product);

		// Assert
		assertEquals(2, history.size());
		assertEquals(new BigDecimal("1.55"), history.get(0).getPrice()); // Ordered descending
		assertEquals(new BigDecimal("1.49"), history.get(1).getPrice());
	}
}
